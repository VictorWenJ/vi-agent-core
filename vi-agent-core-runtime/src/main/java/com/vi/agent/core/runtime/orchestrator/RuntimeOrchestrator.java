package com.vi.agent.core.runtime.orchestrator;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.common.util.ValidationUtils;
import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.runtime.*;
import com.vi.agent.core.model.session.SessionResolutionResult;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.runtime.command.RuntimeExecuteCommand;
import com.vi.agent.core.runtime.engine.AssistantStreamListener;
import com.vi.agent.core.runtime.engine.AgentLoopEngine;
import com.vi.agent.core.runtime.event.RuntimeEvent;
import com.vi.agent.core.runtime.event.RuntimeEventType;
import com.vi.agent.core.runtime.factory.MessageFactory;
import com.vi.agent.core.runtime.factory.RunIdentityFactory;
import com.vi.agent.core.runtime.lifecycle.TurnLifecycleService;
import com.vi.agent.core.runtime.lifecycle.TurnDedupResult;
import com.vi.agent.core.runtime.mdc.MdcScope;
import com.vi.agent.core.runtime.mdc.RuntimeMdcManager;
import com.vi.agent.core.runtime.persistence.PersistenceCoordinator;
import com.vi.agent.core.runtime.result.AgentExecutionResult;
import com.vi.agent.core.runtime.session.SessionResolutionService;
import com.vi.agent.core.runtime.state.SessionStateLoader;
import com.vi.agent.core.runtime.tool.ToolGateway;
import com.vi.agent.core.model.port.SessionLockRepository;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * Runtime run-level orchestrator.
 */
@Slf4j
@Component
public class RuntimeOrchestrator {

    private static final Duration DEFAULT_LOCK_TTL = Duration.ofSeconds(60);

    @Resource
    private SessionResolutionService sessionResolutionService;

    @Resource
    private RunIdentityFactory runIdentityFactory;

    @Resource
    private TurnLifecycleService turnLifecycleService;

    @Resource
    private SessionStateLoader sessionStateLoader;

    @Resource
    private MessageFactory messageFactory;

    @Resource
    private PersistenceCoordinator persistenceCoordinator;

    @Resource
    private RuntimeMdcManager runtimeMdcManager;

    @Resource
    private AgentLoopEngine agentLoopEngine;

    @Resource
    private ToolGateway toolGateway;

    @Resource
    private SessionLockRepository sessionLockRepository;

    public AgentExecutionResult execute(RuntimeExecuteCommand command) {
        return executeInternal(command, null, false);
    }

    public void executeStreaming(RuntimeExecuteCommand command, Consumer<RuntimeEvent> eventConsumer) {
        executeInternal(command, eventConsumer, true);
    }

    private AgentExecutionResult executeInternal(RuntimeExecuteCommand command, Consumer<RuntimeEvent> eventConsumer, boolean streaming) {
        ValidationUtils.requireNonBlank(command.getRequestId(), "requestId");
        ValidationUtils.requireNonBlank(command.getMessage(), "message");

        // 1.找到上一轮运行结果，如果request重复，就将上一轮运行结果返回
        TurnDedupResult turnDedupResult = turnLifecycleService.findAndBuildByRequestId(command.getRequestId());
        log.info("RuntimeOrchestrator executeInternal turnDedupResult={}", JsonUtils.toJson(turnDedupResult));
        if (turnDedupResult != null) {
            return handleDedupResult(command, turnDedupResult, eventConsumer);
        }

        // 2.找到在活动的runtime session，如果没有就创建
        SessionResolutionResult sessionResolutionResult = sessionResolutionService.resolve(command);
        log.info("RuntimeOrchestrator executeInternal sessionResolutionResult={}", JsonUtils.toJson(sessionResolutionResult));

        // 3.创建运行需要的信息
        RunMetadata runMetadata = runIdentityFactory.createRunMetadata();
        String sessionId = sessionResolutionResult.getSession().getSessionId();


        // 4.加锁处理
        handleLock(sessionId, runMetadata);

        Turn turn = null;
        AgentRunContext runContext = null;
        try (MdcScope ignored = runtimeMdcManager.open(command.getRequestId(), sessionResolutionResult.getConversation().getConversationId(), sessionId, runMetadata)) {
            // 5.创建用户消息信息
            UserMessage userMessage = messageFactory.createUserMessage(sessionId, runMetadata.getTurnId(), command.getMessage());
            log.info("RuntimeOrchestrator executeInternal userMessage={}", JsonUtils.toJson(userMessage));

            // 6.创建当前轮次信息并保存
            turn = turnLifecycleService.createRunningTurn(
                runMetadata.getTurnId(),
                sessionResolutionResult.getConversation().getConversationId(),
                sessionId,
                command.getRequestId(),
                runMetadata.getRunId(),
                userMessage.getMessageId());
            log.info("RuntimeOrchestrator executeInternal turn={}", JsonUtils.toJson(turn));

            persistenceCoordinator.persistUserMessage(
                sessionResolutionResult.getConversation().getConversationId(),
                sessionId,
                userMessage
            );

            // 7.加载历史消息
            List<Message> historyMassages = sessionStateLoader.load(sessionResolutionResult.getConversation().getConversationId(), sessionId);
            log.info("RuntimeOrchestrator executeInternal historyMassages={}", JsonUtils.toJson(historyMassages));

            historyMassages.add(userMessage);

            // 8.构建当前上下文
            runContext = AgentRunContext.builder()
                .runMetadata(runMetadata)
                .conversation(sessionResolutionResult.getConversation())
                .session(sessionResolutionResult.getSession())
                .turn(turn)
                .userInput(command.getMessage())
                .workingMessages(historyMassages)
                .availableTools(toolGateway.listDefinitions())
                .state(AgentRunState.STARTED)
                .iteration(0)
                .build();
            log.info("RuntimeOrchestrator executeInternal historyMassages={}", JsonUtils.toJson(historyMassages));

            emit(eventConsumer, RuntimeEvent.builder()
                .eventType(RuntimeEventType.RUN_STARTED)
                .runStatus(RunStatus.RUNNING)
                .requestId(command.getRequestId())
                .conversationId(sessionResolutionResult.getConversation().getConversationId())
                .sessionId(sessionId)
                .turnId(turn.getTurnId())
                .runId(runMetadata.getRunId())
                .build());

            // 9.loop engine处理
            final String streamingTurnId = turn.getTurnId();
            final String streamingRunId = runMetadata.getRunId();
            final String streamingConversationId = sessionResolutionResult.getConversation().getConversationId();
            LoopExecutionResult loopResult = streaming
                ? agentLoopEngine.runStreaming(runContext, new AssistantStreamListener() {
                    @Override
                    public void onMessageStarted(String assistantMessageId) {
                        emit(eventConsumer, RuntimeEvent.builder()
                            .eventType(RuntimeEventType.MESSAGE_STARTED)
                            .runStatus(RunStatus.RUNNING)
                            .requestId(command.getRequestId())
                            .conversationId(streamingConversationId)
                            .sessionId(sessionId)
                            .turnId(streamingTurnId)
                            .runId(streamingRunId)
                            .messageId(assistantMessageId)
                            .build());
                    }

                    @Override
                    public void onMessageDelta(String assistantMessageId, String delta) {
                        emit(eventConsumer, RuntimeEvent.builder()
                            .eventType(RuntimeEventType.MESSAGE_DELTA)
                            .runStatus(RunStatus.RUNNING)
                            .requestId(command.getRequestId())
                            .conversationId(streamingConversationId)
                            .sessionId(sessionId)
                            .turnId(streamingTurnId)
                            .runId(streamingRunId)
                            .messageId(assistantMessageId)
                            .delta(delta)
                            .build());
                    }

                    @Override
                    public void onMessageCompleted(AssistantMessage assistantMessage, FinishReason finishReason) {
                        emit(eventConsumer, RuntimeEvent.builder()
                            .eventType(RuntimeEventType.MESSAGE_COMPLETED)
                            .runStatus(RunStatus.RUNNING)
                            .requestId(command.getRequestId())
                            .conversationId(streamingConversationId)
                            .sessionId(sessionId)
                            .turnId(streamingTurnId)
                            .runId(streamingRunId)
                            .messageId(assistantMessage.getMessageId())
                            .content(assistantMessage.getContent())
                            .finishReason(finishReason)
                            .build());
                    }
                })
                : agentLoopEngine.run(runContext);
            log.info("RuntimeOrchestrator executeInternal loopResult={}", JsonUtils.toJson(loopResult));

            for (var toolCall : loopResult.getToolCalls()) {
                emit(eventConsumer, RuntimeEvent.builder()
                    .eventType(RuntimeEventType.TOOL_CALL)
                    .runStatus(RunStatus.RUNNING)
                    .requestId(command.getRequestId())
                    .conversationId(sessionResolutionResult.getConversation().getConversationId())
                    .sessionId(sessionId)
                    .turnId(turn.getTurnId())
                    .runId(runMetadata.getRunId())
                    .messageId(toolCall.getMessageId())
                    .toolCall(toolCall)
                    .build());
            }
            for (var toolResult : loopResult.getToolResults()) {
                emit(eventConsumer, RuntimeEvent.builder()
                    .eventType(RuntimeEventType.TOOL_RESULT)
                    .runStatus(RunStatus.RUNNING)
                    .requestId(command.getRequestId())
                    .conversationId(sessionResolutionResult.getConversation().getConversationId())
                    .sessionId(sessionId)
                    .turnId(turn.getTurnId())
                    .runId(runMetadata.getRunId())
                    .messageId(toolResult.getMessageId())
                    .toolResult(toolResult)
                    .build());
            }

            runContext.markCompleted();
            persistenceCoordinator.persistSuccess(runContext, loopResult);

            emit(eventConsumer, RuntimeEvent.builder()
                .eventType(RuntimeEventType.RUN_COMPLETED)
                .runStatus(RunStatus.COMPLETED)
                .requestId(command.getRequestId())
                .conversationId(sessionResolutionResult.getConversation().getConversationId())
                .sessionId(sessionId)
                .turnId(turn.getTurnId())
                .runId(runMetadata.getRunId())
                .finishReason(loopResult.getFinishReason())
                .usage(loopResult.getUsage())
                .content(loopResult.getAssistantMessage().getContent())
                .build());

            return AgentExecutionResult.builder()
                .requestId(command.getRequestId())
                .runStatus(RunStatus.COMPLETED)
                .conversationId(sessionResolutionResult.getConversation().getConversationId())
                .sessionId(sessionId)
                .turnId(turn.getTurnId())
                .userMessageId(userMessage.getMessageId())
                .assistantMessageId(loopResult.getAssistantMessage().getMessageId())
                .runId(runMetadata.getRunId())
                .finalAssistantMessage(loopResult.getAssistantMessage())
                .finishReason(loopResult.getFinishReason())
                .usage(loopResult.getUsage())
                .createdAt(Instant.now())
                .build();
        } catch (AgentRuntimeException e) {
            if (runContext != null) {
                runContext.markFailed();
            }
            if (turn != null) {
                if (runContext != null) {
                    persistenceCoordinator.persistFailure(runContext, e.getErrorCode().getCode(), e.getMessage());
                } else {
                    turnLifecycleService.failTurn(turn, e.getErrorCode().getCode(), e.getMessage());
                }
            }
            emitFailed(eventConsumer, command, sessionResolutionResult, runMetadata, turn, e.getErrorCode().getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            if (runContext != null) {
                runContext.markFailed();
            }
            if (turn != null) {
                if (runContext != null) {
                    persistenceCoordinator.persistFailure(runContext, ErrorCode.RUNTIME_EXECUTION_FAILED.getCode(), e.getMessage());
                } else {
                    turnLifecycleService.failTurn(turn, ErrorCode.RUNTIME_EXECUTION_FAILED.getCode(), e.getMessage());
                }
            }
            emitFailed(eventConsumer, command, sessionResolutionResult, runMetadata, turn, ErrorCode.RUNTIME_EXECUTION_FAILED.getCode(), e.getMessage());
            throw new AgentRuntimeException(ErrorCode.RUNTIME_EXECUTION_FAILED, "runtime execution failed", e);
        } finally {
            sessionLockRepository.unlock(sessionId, runMetadata.getRunId());
            messageFactory.clearSessionSequenceCursor(sessionId);
        }
    }

    private void handleLock(String sessionId, RunMetadata runMetadata) {
        // 锁住当前runtime session和轮次
        if (!sessionLockRepository.tryLock(sessionId, runMetadata.getRunId(), DEFAULT_LOCK_TTL)) {
            throw new AgentRuntimeException(ErrorCode.SESSION_CONCURRENT_REQUEST, "session has another running request");
        }

        // 如果有在活动的轮次，说明冲突，就把刚才加的锁释放
        if (turnLifecycleService.existsRunningTurn(sessionId)) {
            sessionLockRepository.unlock(sessionId, runMetadata.getRunId());
            throw new AgentRuntimeException(ErrorCode.SESSION_CONCURRENT_REQUEST, "session has another running request");
        }
    }

    private AgentExecutionResult handleDedupResult(RuntimeExecuteCommand command, TurnDedupResult turnDedupResult, Consumer<RuntimeEvent> eventConsumer) {
        Turn turn = turnDedupResult.getTurn();
        switch (turnDedupResult.getStatus()){
            case COMPLETED -> {
                AssistantMessage assistantMessage = buildAssistantMessage(turnDedupResult.getAssistantMessage(), turn.getTurnId());
                return AgentExecutionResult.builder()
                    .requestId(command.getRequestId())
                    .runStatus(RunStatus.COMPLETED)
                    .conversationId(turn.getConversationId())
                    .sessionId(turn.getSessionId())
                    .turnId(turn.getTurnId())
                    .userMessageId(turn.getUserMessageId())
                    .assistantMessageId(turn.getAssistantMessageId())
                    .runId(turn.getRunId())
                    .finalAssistantMessage(assistantMessage)
                    .finishReason(turn.getFinishReason())
                    .usage(turn.getUsage())
                    .createdAt(Instant.now())
                    .build();
            }
            case RUNNING -> {
                emit(eventConsumer, RuntimeEvent.builder()
                    .eventType(RuntimeEventType.RUN_STARTED)
                    .runStatus(RunStatus.RUNNING)
                    .requestId(command.getRequestId())
                    .conversationId(turn.getConversationId())
                    .sessionId(turn.getSessionId())
                    .turnId(turn.getTurnId())
                    .runId(turn.getRunId())
                    .content("processing")
                    .build());

                return AgentExecutionResult.builder()
                    .requestId(command.getRequestId())
                    .runStatus(RunStatus.RUNNING)
                    .conversationId(turn.getConversationId())
                    .sessionId(turn.getSessionId())
                    .turnId(turn.getTurnId())
                    .userMessageId(turn.getUserMessageId())
                    .assistantMessageId(turn.getAssistantMessageId())
                    .runId(turn.getRunId())
                    .createdAt(Instant.now())
                    .build();
            }
            default -> {
                return AgentExecutionResult.builder()
                    .requestId(command.getRequestId())
                    .runStatus(RunStatus.FAILED)
                    .conversationId(turn.getConversationId())
                    .sessionId(turn.getSessionId())
                    .turnId(turn.getTurnId())
                    .userMessageId(turn.getUserMessageId())
                    .assistantMessageId(turn.getAssistantMessageId())
                    .runId(turn.getRunId())
                    .finishReason(FinishReason.ERROR)
                    .createdAt(Instant.now())
                    .build();
            }
        }
    }

    private AssistantMessage buildAssistantMessage(Message message, String turnId) {
        if (message instanceof AssistantMessage assistantMessage) {
            return assistantMessage;
        }
        if (message == null) {
            return AssistantMessage.create(null, turnId, 0L, "", List.of());
        }
        return AssistantMessage.create(
            message.getMessageId(),
            message.getTurnId(),
            message.getSequenceNo(),
            message.getContent(),
            List.of()
        );
    }

    private void emitFailed(
        Consumer<RuntimeEvent> eventConsumer,
        RuntimeExecuteCommand command,
        SessionResolutionResult resolution,
        RunMetadata runMetadata,
        Turn turn,
        String errorCode,
        String errorMessage
    ) {
        emit(eventConsumer, RuntimeEvent.builder()
            .eventType(RuntimeEventType.RUN_FAILED)
            .runStatus(RunStatus.FAILED)
            .requestId(command.getRequestId())
            .conversationId(resolution.getConversation().getConversationId())
            .sessionId(resolution.getSession().getSessionId())
            .turnId(turn == null ? runMetadata.getTurnId() : turn.getTurnId())
            .runId(runMetadata.getRunId())
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .errorType("SYSTEM")
            .retryable(false)
            .build());
    }

    private void emit(Consumer<RuntimeEvent> eventConsumer, RuntimeEvent event) {
        if (eventConsumer == null || event == null) {
            return;
        }
        eventConsumer.accept(event);
    }
}

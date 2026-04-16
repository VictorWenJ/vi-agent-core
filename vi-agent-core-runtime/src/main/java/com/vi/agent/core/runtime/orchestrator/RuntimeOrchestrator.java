package com.vi.agent.core.runtime.orchestrator;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.common.id.ConversationIdGenerator;
import com.vi.agent.core.common.id.MessageIdGenerator;
import com.vi.agent.core.common.id.RunIdGenerator;
import com.vi.agent.core.common.id.ToolCallIdGenerator;
import com.vi.agent.core.common.id.TraceIdGenerator;
import com.vi.agent.core.common.id.TurnIdGenerator;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.common.util.ValidationUtils;
import com.vi.agent.core.model.message.*;
import com.vi.agent.core.model.message.ToolExecutionMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.AgentRunState;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolResult;
import com.vi.agent.core.model.transcript.ConversationTranscript;
import com.vi.agent.core.runtime.context.ContextAssembler;
import com.vi.agent.core.runtime.engine.AgentLoopEngine;
import com.vi.agent.core.runtime.event.RuntimeEvent;
import com.vi.agent.core.runtime.event.RuntimeEventType;
import com.vi.agent.core.runtime.port.TranscriptStore;
import com.vi.agent.core.runtime.result.AgentExecutionResult;
import com.vi.agent.core.runtime.tool.ToolGateway;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Runtime 核心编排入口（唯一主链路编排中心）。
 */
@Slf4j
public class RuntimeOrchestrator {

    private static final int DEFAULT_MAX_ITERATIONS = 6;

    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_RUN_ID = "runId";
    private static final String MDC_SESSION_ID = "sessionId";
    private static final String MDC_CONVERSATION_ID = "conversationId";
    private static final String MDC_TURN_ID = "turnId";
    private static final String MDC_MESSAGE_ID = "messageId";
    private static final String MDC_TOOL_CALL_ID = "toolCallId";

    /**
     * 上下文装配器。
     */
    private final ContextAssembler contextAssembler;

    /**
     * Agent Loop 引擎。
     */
    private final AgentLoopEngine agentLoopEngine;

    /**
     * 工具网关。
     */
    private final ToolGateway toolGateway;

    /**
     * Transcript 存储接口。
     */
    private final TranscriptStore transcriptStore;

    /**
     * traceId 生成器。
     */
    private final TraceIdGenerator traceIdGenerator;

    /**
     * runId 生成器。
     */
    private final RunIdGenerator runIdGenerator;

    /**
     * conversationId 生成器。
     */
    private final ConversationIdGenerator conversationIdGenerator;

    /**
     * turnId 生成器。
     */
    private final TurnIdGenerator turnIdGenerator;

    /**
     * messageId 生成器。
     */
    private final MessageIdGenerator messageIdGenerator;

    /**
     * toolCallId 生成器。
     */
    private final ToolCallIdGenerator toolCallIdGenerator;

    /**
     * 最大循环次数。
     */
    private final int maxIterations;

    public RuntimeOrchestrator(
        ContextAssembler contextAssembler,
        AgentLoopEngine agentLoopEngine,
        ToolGateway toolGateway,
        TranscriptStore transcriptStore,
        TraceIdGenerator traceIdGenerator,
        RunIdGenerator runIdGenerator,
        ConversationIdGenerator conversationIdGenerator,
        TurnIdGenerator turnIdGenerator,
        MessageIdGenerator messageIdGenerator,
        ToolCallIdGenerator toolCallIdGenerator
    ) {
        this(
            contextAssembler,
            agentLoopEngine,
            toolGateway,
            transcriptStore,
            traceIdGenerator,
            runIdGenerator,
            conversationIdGenerator,
            turnIdGenerator,
            messageIdGenerator,
            toolCallIdGenerator,
            DEFAULT_MAX_ITERATIONS
        );
    }

    public RuntimeOrchestrator(
        ContextAssembler contextAssembler,
        AgentLoopEngine agentLoopEngine,
        ToolGateway toolGateway,
        TranscriptStore transcriptStore,
        TraceIdGenerator traceIdGenerator,
        RunIdGenerator runIdGenerator,
        ConversationIdGenerator conversationIdGenerator,
        TurnIdGenerator turnIdGenerator,
        MessageIdGenerator messageIdGenerator,
        ToolCallIdGenerator toolCallIdGenerator,
        int maxIterations
    ) {
        this.contextAssembler = contextAssembler;
        this.agentLoopEngine = agentLoopEngine;
        this.toolGateway = toolGateway;
        this.transcriptStore = transcriptStore;
        this.traceIdGenerator = traceIdGenerator;
        this.runIdGenerator = runIdGenerator;
        this.conversationIdGenerator = conversationIdGenerator;
        this.turnIdGenerator = turnIdGenerator;
        this.messageIdGenerator = messageIdGenerator;
        this.toolCallIdGenerator = toolCallIdGenerator;
        this.maxIterations = maxIterations <= 0 ? DEFAULT_MAX_ITERATIONS : maxIterations;
    }

    /**
     * 执行一次同步会话。
     *
     * @param sessionId 会话 ID
     * @param userInput 用户输入
     * @return 执行结果
     */
    public AgentExecutionResult execute(String sessionId, String userInput) {
        return executeInternal(sessionId, userInput, null, false);
    }

    /**
     * 执行一次流式会话。
     *
     * @param sessionId     会话 ID
     * @param userInput     用户输入
     * @param eventConsumer 事件消费器
     * @return 执行结果
     */
    public AgentExecutionResult executeStreaming(String sessionId, String userInput, Consumer<RuntimeEvent> eventConsumer) {
        return executeInternal(sessionId, userInput, eventConsumer, true);
    }

    private AgentExecutionResult executeInternal(String sessionId, String userInput, Consumer<RuntimeEvent> eventConsumer, boolean streaming) {
        ValidationUtils.requireNonBlank(sessionId, "sessionId");
        ValidationUtils.requireNonBlank(userInput, "userInput");

        String traceId = traceIdGenerator.nextId();
        String runId = runIdGenerator.nextId();
        String turnId = turnIdGenerator.nextId();

        // 1.获取上下文，没有就构建
        ConversationTranscript transcript = transcriptStore.load(sessionId)
            .orElseGet(() -> new ConversationTranscript(sessionId, conversationIdGenerator.nextId()));
        log.info("RuntimeOrchestrator executeInternal transcript={}", JsonUtils.toJson(transcript));
        if (transcript.getConversationId() == null || transcript.getConversationId().isBlank()) {
            transcript.setConversationId(conversationIdGenerator.nextId());
        }

        String conversationId = transcript.getConversationId();

        String previousTraceId = MDC.get(MDC_TRACE_ID);
        String previousRunId = MDC.get(MDC_RUN_ID);
        String previousSessionId = MDC.get(MDC_SESSION_ID);
        String previousConversationId = MDC.get(MDC_CONVERSATION_ID);
        String previousTurnId = MDC.get(MDC_TURN_ID);
        String previousMessageId = MDC.get(MDC_MESSAGE_ID);

        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_RUN_ID, runId);
        MDC.put(MDC_SESSION_ID, sessionId);
        MDC.put(MDC_CONVERSATION_ID, conversationId);
        MDC.put(MDC_TURN_ID, turnId);

        AgentRunContext runContext = null;
        try {
            log.info("executeInternal executeInternal runtime run start traceId={} runId={} sessionId={} conversationId={} turnId={}",
                traceId, runId, sessionId, conversationId, turnId);

            transcript.setTraceId(traceId);
            transcript.setRunId(runId);

            UserMessage userMessage = new UserMessage(messageIdGenerator.nextId(), userInput);
            MDC.put(MDC_MESSAGE_ID, userMessage.getMessageId());

            // 2.萃集历史上下文
            List<Message> workingMessages = contextAssembler.assemble(transcript, userMessage);
            log.info("RuntimeOrchestrator executeInternal workingMessages={}", JsonUtils.toJson(workingMessages));

            transcript.appendMessage(userMessage);

            // 3.本次运行上下文
            runContext = new AgentRunContext(
                traceId,
                runId,
                sessionId,
                conversationId,
                turnId,
                userInput,
                workingMessages,
                toolGateway.listDefinitions(),
                transcript,
                AgentRunState.STARTED
            );
            log.info("RuntimeOrchestrator executeInternal runContext={}", JsonUtils.toJson(runContext));

            // 4.流式事件构建
            safeEmit(eventConsumer, RuntimeEvent.builder()
                .type(RuntimeEventType.START)
                .traceId(traceId)
                .runId(runId)
                .sessionId(sessionId)
                .conversationId(conversationId)
                .turnId(turnId)
                .content("")
                .done(false)
                .build());

            AssistantMessage finalAssistant = null;
            // 5.循环执行
            for (int iteration = 1; iteration <= maxIterations; iteration++) {
                //设置循环执行轮次
                runContext.setIteration(iteration);
                log.info("RuntimeOrchestrator executeInternal runtime loop iteration={} traceId={} runId={} turnId={}", iteration, traceId, runId, turnId);

                safeEmit(eventConsumer, RuntimeEvent.builder()
                    .type(RuntimeEventType.ITERATION)
                    .traceId(traceId)
                    .runId(runId)
                    .sessionId(sessionId)
                    .conversationId(conversationId)
                    .turnId(turnId)
                    .content(String.valueOf(iteration))
                    .done(false)
                    .build());

                // 6.流式输出：streamAgentLoopEngine；同步输出：agentLoopEngine
                AssistantMessage assistantMessage;
                assistantMessage = agentLoopEngine.run(runContext);
                log.info("RuntimeOrchestrator executeInternal assistantMessage={}", JsonUtils.toJson(assistantMessage));

                AssistantMessage normalizedAssistant = normalizeAssistantMessage(assistantMessage);
                MDC.put(MDC_MESSAGE_ID, normalizedAssistant.getMessageId());
                runContext.appendWorkingMessage(normalizedAssistant);
                transcript.appendMessage(normalizedAssistant);

                if (normalizedAssistant.getToolCalls().isEmpty()) {
                    finalAssistant = normalizedAssistant;
                    break;
                }

                for (ToolCall rawToolCall : normalizedAssistant.getToolCalls()) {
                    ToolCall toolCall = normalizeToolCall(rawToolCall, turnId);
                    transcript.appendToolCall(toolCall);

                    String previousToolCallId = MDC.get(MDC_TOOL_CALL_ID);
                    MDC.put(MDC_TOOL_CALL_ID, toolCall.getToolCallId());
                    try {
                        safeEmit(eventConsumer, RuntimeEvent.builder()
                            .type(RuntimeEventType.TOOL_CALL)
                            .traceId(traceId)
                            .runId(runId)
                            .sessionId(sessionId)
                            .conversationId(conversationId)
                            .turnId(turnId)
                            .content(toolCall.getToolName())
                            .done(false)
                            .build());

                        ToolResult toolResult = safeExecuteTool(toolCall, turnId);
                        transcript.appendToolResult(toolResult);
                        safeEmit(eventConsumer, RuntimeEvent.builder()
                            .type(RuntimeEventType.TOOL_RESULT)
                            .traceId(traceId)
                            .runId(runId)
                            .sessionId(sessionId)
                            .conversationId(conversationId)
                            .turnId(turnId)
                            .content(toolResult.getOutput())
                            .done(false)
                            .build());

                        ToolExecutionMessage toolExecutionMessage = new ToolExecutionMessage(
                            messageIdGenerator.nextId(),
                            toolResult.getToolCallId(),
                            toolResult.getToolName(),
                            formatToolOutput(toolResult),
                            Instant.now()
                        );
                        MDC.put(MDC_MESSAGE_ID, toolExecutionMessage.getMessageId());
                        runContext.appendWorkingMessage(toolExecutionMessage);
                        transcript.appendMessage(toolExecutionMessage);
                    } finally {
                        restoreMdcValue(MDC_TOOL_CALL_ID, previousToolCallId);
                    }
                }
            }

            if (finalAssistant == null) {
                throw new AgentRuntimeException(
                    ErrorCode.RUNTIME_MAX_ITERATIONS_EXCEEDED,
                    "超过最大循环次数: " + maxIterations
                );
            }

            runContext.setAgentRunState(AgentRunState.COMPLETED);
            transcriptStore.save(transcript);

            safeEmit(eventConsumer, RuntimeEvent.builder()
                .type(RuntimeEventType.COMPLETE)
                .traceId(traceId)
                .runId(runId)
                .sessionId(sessionId)
                .conversationId(conversationId)
                .turnId(turnId)
                .content(Optional.ofNullable(finalAssistant.getContent()).orElse(""))
                .done(true)
                .build());

            log.info("Runtime run complete traceId={} runId={} sessionId={} conversationId={} turnId={} messages={} toolCalls={} toolResults={}",
                traceId,
                runId,
                sessionId,
                conversationId,
                turnId,
                transcript.getMessages().size(),
                transcript.getToolCalls().size(),
                transcript.getToolResults().size());

            return AgentExecutionResult.builder()
                .traceId(traceId)
                .runId(runId)
                .sessionId(sessionId)
                .conversationId(conversationId)
                .turnId(turnId)
                .assistantMessage(finalAssistant)
                .build();
        } catch (AgentRuntimeException e) {
            if (runContext != null) {
                runContext.setAgentRunState(AgentRunState.FAILED);
            }
            safeEmit(eventConsumer, RuntimeEvent.builder()
                .type(RuntimeEventType.ERROR)
                .traceId(traceId)
                .runId(runId)
                .sessionId(sessionId)
                .conversationId(conversationId)
                .turnId(turnId)
                .content(e.getMessage())
                .done(true)
                .build());
            safeSaveTranscript(transcript);
            log.error("Runtime run failed traceId={} runId={} sessionId={} conversationId={} turnId={}",
                traceId, runId, sessionId, conversationId, turnId, e);
            throw e;
        } catch (Exception e) {
            if (runContext != null) {
                runContext.setAgentRunState(AgentRunState.FAILED);
            }
            safeEmit(eventConsumer, RuntimeEvent.builder()
                .type(RuntimeEventType.ERROR)
                .traceId(traceId)
                .runId(runId)
                .sessionId(sessionId)
                .conversationId(conversationId)
                .turnId(turnId)
                .content(e.getMessage())
                .done(true)
                .build());
            safeSaveTranscript(transcript);
            log.error("Runtime run unexpected failed traceId={} runId={} sessionId={} conversationId={} turnId={}",
                traceId, runId, sessionId, conversationId, turnId, e);
            throw new AgentRuntimeException(ErrorCode.RUNTIME_EXECUTION_FAILED, "运行时执行失败", e);
        } finally {
            restoreMdcValue(MDC_TRACE_ID, previousTraceId);
            restoreMdcValue(MDC_RUN_ID, previousRunId);
            restoreMdcValue(MDC_SESSION_ID, previousSessionId);
            restoreMdcValue(MDC_CONVERSATION_ID, previousConversationId);
            restoreMdcValue(MDC_TURN_ID, previousTurnId);
            restoreMdcValue(MDC_MESSAGE_ID, previousMessageId);
            MDC.remove(MDC_TOOL_CALL_ID);
        }
    }

    private AssistantMessage normalizeAssistantMessage(AssistantMessage assistantMessage) {
        if (assistantMessage == null) {
            throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, "模型返回空助手消息");
        }
        return new AssistantMessage(
            messageIdGenerator.nextId(),
            Optional.ofNullable(assistantMessage.getContent()).orElse(""),
            assistantMessage.getToolCalls(),
            Instant.now()
        );
    }

    private ToolCall normalizeToolCall(ToolCall rawToolCall, String turnId) {
        if (rawToolCall == null || rawToolCall.getToolName() == null || rawToolCall.getToolName().isBlank()) {
            throw new AgentRuntimeException(ErrorCode.INVALID_ARGUMENT, "模型返回非法工具调用");
        }
        return ToolCall.builder()
            .toolCallId((rawToolCall.getToolCallId() == null || rawToolCall.getToolCallId().isBlank())
                ? toolCallIdGenerator.nextId()
                : rawToolCall.getToolCallId())
            .toolName(rawToolCall.getToolName())
            .argumentsJson(Optional.ofNullable(rawToolCall.getArgumentsJson()).orElse("{}"))
            .turnId(Objects.requireNonNullElse(rawToolCall.getTurnId(), turnId))
            .build();
    }

    private ToolResult safeExecuteTool(ToolCall toolCall, String turnId) {
        try {
            ToolResult result = toolGateway.route(toolCall);
            if (result == null) {
                return ToolResult.builder()
                    .toolCallId(toolCall.getToolCallId())
                    .toolName(toolCall.getToolName())
                    .turnId(turnId)
                    .success(false)
                    .output("")
                    .errorMessage("工具返回空结果")
                    .build();
            }
            if (result.getToolCallId() == null || result.getToolCallId().isBlank()) {
                result.setToolCallId(toolCall.getToolCallId());
            }
            if (result.getToolName() == null || result.getToolName().isBlank()) {
                result.setToolName(toolCall.getToolName());
            }
            if (result.getTurnId() == null || result.getTurnId().isBlank()) {
                result.setTurnId(turnId);
            }
            return result;
        } catch (Exception e) {
            log.error("Tool execute failed toolName={} toolCallId={}", toolCall.getToolName(), toolCall.getToolCallId(), e);
            return ToolResult.builder()
                .toolCallId(toolCall.getToolCallId())
                .toolName(toolCall.getToolName())
                .turnId(turnId)
                .success(false)
                .output("")
                .errorMessage(e.getMessage())
                .build();
        }
    }

    private String formatToolOutput(ToolResult toolResult) {
        if (toolResult.isSuccess()) {
            return Optional.ofNullable(toolResult.getOutput()).orElse("");
        }
        return "TOOL_ERROR: " + Optional.ofNullable(toolResult.getErrorMessage()).orElse("unknown error");
    }

    private void safeSaveTranscript(ConversationTranscript transcript) {
        try {
            transcriptStore.save(transcript);
        } catch (Exception saveError) {
            log.error("Transcript save after error failed sessionId={}", transcript.getSessionId(), saveError);
        }
    }

    private void safeEmit(Consumer<RuntimeEvent> eventConsumer, RuntimeEvent event) {
        if (eventConsumer == null || event == null) {
            return;
        }
        try {
            eventConsumer.accept(event);
        } catch (Exception e) {
            log.warn("Runtime stream event consumer failed type={}", event.getType(), e);
        }
    }

    private void restoreMdcValue(String key, String value) {
        if (value == null) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, value);
    }
}

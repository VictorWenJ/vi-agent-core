package com.vi.agent.core.runtime.persistence;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.ConversationRepository;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.port.RunEventRepository;
import com.vi.agent.core.model.port.SessionRepository;
import com.vi.agent.core.model.port.SessionWorkingSetRepository;
import com.vi.agent.core.model.port.TurnRepository;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.model.runtime.RunEventActorType;
import com.vi.agent.core.model.runtime.RunEventRecord;
import com.vi.agent.core.model.runtime.RunEventType;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.memory.SessionWorkingSetSnapshot;
import com.vi.agent.core.model.tool.ToolCallStatus;
import com.vi.agent.core.model.tool.ToolExecution;
import com.vi.agent.core.model.tool.ToolExecutionStatus;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.runtime.factory.RunIdentityFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Single-turn persistence coordinator.
 */
@Slf4j
@Service
public class PersistenceCoordinator {

    @Resource
    private MessageRepository messageRepository;

    @Resource
    private TurnRepository turnRepository;

    @Resource
    private SessionRepository sessionRepository;

    @Resource
    private ConversationRepository conversationRepository;

    @Resource
    private SessionWorkingSetRepository sessionWorkingSetRepository;

    @Resource
    private RunEventRepository runEventRepository;

    @Resource
    private RunIdentityFactory runIdentityFactory;

    @Resource
    private SessionWorkingSetLoader sessionWorkingSetLoader;

    public List<Message> load(String conversationId, String sessionId) {
        return sessionWorkingSetLoader.load(conversationId, sessionId);
    }

    public void refresh(String conversationId, String sessionId, List<Message> messages) {
        sessionWorkingSetRepository.save(SessionWorkingSetSnapshot.builder()
            .sessionId(sessionId)
            .conversationId(conversationId)
            .rawMessageIds(messages == null ? List.of() : messages.stream().map(Message::getMessageId).toList())
            .messages(messages == null ? List.of() : new ArrayList<>(messages))
            .updatedAt(Instant.now())
            .build());
    }

    public void persistUserMessage(UserMessage userMessage) {
        messageRepository.save(userMessage);
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistSuccess(AgentRunContext runContext, LoopExecutionResult loopExecutionResult) {
        if (CollectionUtils.isNotEmpty(loopExecutionResult.getAppendedMessages())) {
            messageRepository.saveBatch(loopExecutionResult.getAppendedMessages());
        }

        Turn turn = runContext.getTurn();
        turn.markCompleted(
            loopExecutionResult.getFinishReason(),
            loopExecutionResult.getUsage(),
            Instant.now(),
            loopExecutionResult.getAssistantMessage().getMessageId()
        );
        turnRepository.update(turn);

        Session session = runContext.getSession();
        session.touch(Instant.now());
        sessionRepository.update(session);

        Conversation conversation = runContext.getConversation();
        conversation.activateSession(session.getSessionId());
        conversation.touchLastMessageAt(Instant.now());
        conversationRepository.update(conversation);

        runEventRepository.saveBatch(List.of(buildRunCompletedEvent(runContext, loopExecutionResult)));

        String conversationId = conversation.getConversationId();
        String sessionId = session.getSessionId();
        List<Message> workingMessages = new ArrayList<>(runContext.getWorkingMessages());
        registerAfterCommit(() -> {
            try {
                refresh(conversationId, sessionId, workingMessages);
            } catch (Exception ex) {
                log.warn("Refresh redis session working set failed, sessionId={}", sessionId, ex);
                safeEvictSessionWorkingSet(sessionId);
            }
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistFailure(AgentRunContext runContext, String errorCode, String errorMessage) {
        persistAssistantToolDecisionMessages(runContext);
        cancelPendingToolCalls(runContext);
        messageRepository.saveFailureToolFacts(runContext.getToolCalls(), runContext.getToolExecutions());
        runEventRepository.saveBatch(List.of(buildRunFailedEvent(runContext, errorCode, errorMessage)));

        Turn turn = runContext.getTurn();
        turn.markFailed(errorCode, errorMessage, Instant.now());
        turnRepository.update(turn);

        Session session = runContext.getSession();
        session.touch(Instant.now());
        sessionRepository.update(session);

        registerAfterCommit(() -> safeEvictSessionWorkingSet(session.getSessionId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistAssistantToolDecision(AgentRunContext runContext, AssistantMessage assistantMessage) {
        if (assistantMessage == null || CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
            return;
        }
        messageRepository.saveAssistantMessageIfAbsent(assistantMessage);
        for (AssistantToolCall toolCall : assistantMessage.getToolCalls()) {
            messageRepository.saveToolCallCreated(toolCall);
            runEventRepository.saveBatch(List.of(buildToolEvent(
                runContext, RunEventType.TOOL_CALL_CREATED, RunEventActorType.MODEL, toolCall, null
            )));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistToolDispatched(AgentRunContext runContext, AssistantToolCall toolCall) {
        messageRepository.updateToolCallStatus(toolCall.getToolCallRecordId(), ToolCallStatus.DISPATCHED);
        runEventRepository.saveBatch(List.of(buildToolEvent(
            runContext, RunEventType.TOOL_DISPATCHED, RunEventActorType.TOOL, toolCall, null
        )));
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistToolStarted(AgentRunContext runContext, AssistantToolCall toolCall, ToolExecution runningExecution) {
        messageRepository.updateToolCallStatus(toolCall.getToolCallRecordId(), ToolCallStatus.RUNNING);
        messageRepository.upsertToolExecutionRunning(runningExecution);
        runEventRepository.saveBatch(List.of(buildToolEvent(
            runContext, RunEventType.TOOL_STARTED, RunEventActorType.TOOL, toolCall, runningExecution
        )));
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistToolCompleted(
        AgentRunContext runContext,
        AssistantToolCall toolCall,
        ToolExecution completedExecution,
        Message toolMessage
    ) {
        if (toolMessage != null) {
            messageRepository.save(toolMessage);
        }
        messageRepository.updateToolExecutionFinal(completedExecution);
        messageRepository.updateToolCallStatus(toolCall.getToolCallRecordId(), ToolCallStatus.SUCCEEDED);
        runEventRepository.saveBatch(List.of(buildToolEvent(
            runContext, RunEventType.TOOL_COMPLETED, RunEventActorType.TOOL, toolCall, completedExecution
        )));
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistToolFailed(AgentRunContext runContext, AssistantToolCall toolCall, ToolExecution failedExecution) {
        messageRepository.updateToolExecutionFinal(failedExecution);
        messageRepository.updateToolCallStatus(toolCall.getToolCallRecordId(), ToolCallStatus.FAILED);
        runEventRepository.saveBatch(List.of(buildToolEvent(
            runContext, RunEventType.TOOL_FAILED, RunEventActorType.TOOL, toolCall, failedExecution
        )));
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistToolCancelled(AgentRunContext runContext, AssistantToolCall cancelledToolCall) {
        messageRepository.updateToolCallStatus(cancelledToolCall.getToolCallRecordId(), ToolCallStatus.CANCELLED);
        runEventRepository.saveBatch(List.of(buildToolEvent(
            runContext, RunEventType.TOOL_CANCELLED, RunEventActorType.TOOL, cancelledToolCall, null
        )));
    }

    private void cancelPendingToolCalls(AgentRunContext runContext) {
        if (runContext == null || CollectionUtils.isEmpty(runContext.getToolCalls())) {
            return;
        }
        Map<String, ToolExecution> executionByRecordId = runContext.getToolExecutions().stream()
            .filter(Objects::nonNull)
            .filter(execution -> execution.getToolCallRecordId() != null)
            .collect(Collectors.toMap(
                ToolExecution::getToolCallRecordId,
                execution -> execution,
                (left, right) -> right,
                LinkedHashMap::new
            ));

        for (AssistantToolCall toolCall : runContext.getToolCalls()) {
            if (toolCall == null || toolCall.getStatus() == null) {
                continue;
            }
            if (toolCall.getStatus() == ToolCallStatus.SUCCEEDED
                || toolCall.getStatus() == ToolCallStatus.FAILED
                || toolCall.getStatus() == ToolCallStatus.CANCELLED) {
                continue;
            }
            ToolExecution relatedExecution = executionByRecordId.get(toolCall.getToolCallRecordId());
            if (relatedExecution == null) {
                AssistantToolCall cancelledToolCall = copyToolCallWithStatus(toolCall, ToolCallStatus.CANCELLED);
                runContext.appendToolCall(cancelledToolCall);
                persistToolCancelled(runContext, cancelledToolCall);
                continue;
            }
            if (relatedExecution.getStatus() == ToolExecutionStatus.RUNNING) {
                ToolExecution failedExecution = copyRunningExecutionAsFailed(relatedExecution);
                AssistantToolCall failedToolCall = copyToolCallWithStatus(toolCall, ToolCallStatus.FAILED);
                runContext.appendToolCall(failedToolCall);
                runContext.appendToolExecution(failedExecution);
                persistToolFailed(runContext, failedToolCall, failedExecution);
            }
        }
    }

    private RunEventRecord buildRunCompletedEvent(AgentRunContext runContext, LoopExecutionResult loopExecutionResult) {
        return RunEventRecord.builder()
            .eventId(runIdentityFactory.nextRunEventId())
            .conversationId(runContext.getConversation().getConversationId())
            .sessionId(runContext.getSession().getSessionId())
            .turnId(runContext.getTurn().getTurnId())
            .runId(runContext.getRunMetadata().getRunId())
            .eventIndex(runContext.nextRunEventIndex())
            .eventType(RunEventType.RUN_COMPLETED)
            .actorType(RunEventActorType.AGENT)
            .actorId(runContext.getRunMetadata().getRunId())
            .payloadJson(JsonUtils.toJson(buildRunCompletedPayload(loopExecutionResult)))
            .createdAt(Instant.now())
            .build();
    }

    private RunEventRecord buildRunFailedEvent(AgentRunContext runContext, String errorCode, String errorMessage) {
        return RunEventRecord.builder()
            .eventId(runIdentityFactory.nextRunEventId())
            .conversationId(runContext.getConversation().getConversationId())
            .sessionId(runContext.getSession().getSessionId())
            .turnId(runContext.getTurn().getTurnId())
            .runId(runContext.getRunMetadata().getRunId())
            .eventIndex(runContext.nextRunEventIndex())
            .eventType(RunEventType.RUN_FAILED)
            .actorType(RunEventActorType.AGENT)
            .actorId(runContext.getRunMetadata().getRunId())
            .payloadJson(JsonUtils.toJson(buildRunFailedPayload(errorCode, errorMessage)))
            .createdAt(Instant.now())
            .build();
    }

    private RunEventRecord buildToolEvent(
        AgentRunContext runContext,
        RunEventType eventType,
        RunEventActorType actorType,
        AssistantToolCall toolCall,
        ToolExecution toolExecution
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolCallRecordId", toolCall == null ? null : toolCall.getToolCallRecordId());
        payload.put("toolCallId", toolCall == null ? null : toolCall.getToolCallId());
        payload.put("toolName", toolCall == null ? (toolExecution == null ? null : toolExecution.getToolName()) : toolCall.getToolName());
        if (toolExecution != null) {
            payload.put("toolExecutionId", toolExecution.getToolExecutionId());
            payload.put("status", toolExecution.getStatus() == null ? null : toolExecution.getStatus().name());
            payload.put("errorCode", toolExecution.getErrorCode());
            payload.put("errorMessage", toolExecution.getErrorMessage());
            payload.put("durationMs", toolExecution.getDurationMs());
        }

        String actorId = switch (actorType) {
            case MODEL -> toolCall == null ? null : toolCall.getAssistantMessageId();
            case TOOL -> toolExecution == null ? (toolCall == null ? null : toolCall.getToolCallRecordId()) : toolExecution.getToolExecutionId();
            case USER, AGENT, SYSTEM -> runContext.getRunMetadata().getRunId();
        };

        return RunEventRecord.builder()
            .eventId(runIdentityFactory.nextRunEventId())
            .conversationId(runContext.getConversation().getConversationId())
            .sessionId(runContext.getSession().getSessionId())
            .turnId(runContext.getTurn().getTurnId())
            .runId(runContext.getRunMetadata().getRunId())
            .eventIndex(runContext.nextRunEventIndex())
            .eventType(eventType)
            .actorType(actorType)
            .actorId(actorId)
            .payloadJson(JsonUtils.toJson(payload))
            .createdAt(Instant.now())
            .build();
    }

    private Map<String, Object> buildRunCompletedPayload(LoopExecutionResult loopExecutionResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("finishReason", loopExecutionResult == null || loopExecutionResult.getFinishReason() == null
            ? null : loopExecutionResult.getFinishReason().name());
        payload.put("usage", loopExecutionResult == null ? null : loopExecutionResult.getUsage());
        return payload;
    }

    private Map<String, Object> buildRunFailedPayload(String errorCode, String errorMessage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("errorCode", errorCode);
        payload.put("errorMessage", errorMessage);
        return payload;
    }

    private AssistantToolCall copyToolCallWithStatus(AssistantToolCall source, ToolCallStatus status) {
        return AssistantToolCall.builder()
            .toolCallRecordId(source.getToolCallRecordId())
            .toolCallId(source.getToolCallId())
            .assistantMessageId(source.getAssistantMessageId())
            .conversationId(source.getConversationId())
            .sessionId(source.getSessionId())
            .turnId(source.getTurnId())
            .runId(source.getRunId())
            .toolName(source.getToolName())
            .argumentsJson(source.getArgumentsJson())
            .callIndex(source.getCallIndex())
            .status(status)
            .createdAt(source.getCreatedAt())
            .build();
    }

    private ToolExecution copyRunningExecutionAsFailed(ToolExecution runningExecution) {
        return ToolExecution.builder()
            .toolExecutionId(runningExecution.getToolExecutionId())
            .toolCallRecordId(runningExecution.getToolCallRecordId())
            .toolCallId(runningExecution.getToolCallId())
            .toolResultMessageId(null)
            .conversationId(runningExecution.getConversationId())
            .sessionId(runningExecution.getSessionId())
            .turnId(runningExecution.getTurnId())
            .runId(runningExecution.getRunId())
            .toolName(runningExecution.getToolName())
            .argumentsJson(runningExecution.getArgumentsJson())
            .outputText(null)
            .outputJson(null)
            .status(ToolExecutionStatus.FAILED)
            .errorCode("TOOL_EXECUTION_FAILED")
            .errorMessage("tool execution interrupted")
            .durationMs(0L)
            .startedAt(runningExecution.getStartedAt())
            .completedAt(Instant.now())
            .createdAt(runningExecution.getCreatedAt())
            .build();
    }

    private void persistAssistantToolDecisionMessages(AgentRunContext runContext) {
        if (runContext == null || CollectionUtils.isEmpty(runContext.getWorkingMessages())) {
            return;
        }
        String currentTurnId = runContext.getTurn().getTurnId();
        for (Message message : runContext.getWorkingMessages()) {
            if (!(message instanceof AssistantMessage assistantMessage)) {
                continue;
            }
            if (assistantMessage.getRole() != MessageRole.ASSISTANT) {
                continue;
            }
            if (!Objects.equals(currentTurnId, assistantMessage.getTurnId())) {
                continue;
            }
            if (CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
                continue;
            }
            messageRepository.saveAssistantMessageIfAbsent(assistantMessage);
            for (AssistantToolCall toolCall : assistantMessage.getToolCalls()) {
                messageRepository.saveToolCallCreated(toolCall);
            }
        }
    }

    private void registerAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    if (action != null) {
                        action.run();
                    }
                }
            });
            return;
        }
        if (action != null) {
            action.run();
        }
    }

    private void safeEvictSessionWorkingSet(String sessionId) {
        try {
            sessionWorkingSetRepository.evict(sessionId);
        } catch (Exception ex) {
            log.warn("Evict redis session working set failed, sessionId={}", sessionId, ex);
        }
    }
}


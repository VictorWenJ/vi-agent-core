package com.vi.agent.core.runtime.persistence;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.ConversationRepository;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.port.RunEventRepository;
import com.vi.agent.core.model.port.SessionRepository;
import com.vi.agent.core.model.port.SessionStateRepository;
import com.vi.agent.core.model.port.TurnRepository;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.model.runtime.RunEventRecord;
import com.vi.agent.core.model.runtime.RunEventType;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.session.SessionStateSnapshot;
import com.vi.agent.core.model.tool.ToolExecution;
import com.vi.agent.core.model.tool.ToolExecutionStatus;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.runtime.factory.RunIdentityFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 单次 turn 持久化协调器。
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
    private SessionStateRepository sessionStateRepository;

    @Resource
    private RunEventRepository runEventRepository;

    @Resource
    private RunIdentityFactory runIdentityFactory;

    @Value("${vi.agent.runtime.session-context.max-messages:200}")
    private int maxMessages;

    public List<Message> load(String conversationId, String sessionId) {
        return sessionStateRepository.findBySessionId(sessionId)
            .map(SessionStateSnapshot::getMessages)
            .orElseGet(() -> reloadFromMysql(conversationId, sessionId));
    }

    public void refresh(String conversationId, String sessionId, List<Message> messages) {
        sessionStateRepository.save(SessionStateSnapshot.builder()
            .sessionId(sessionId)
            .conversationId(conversationId)
            .messages(new ArrayList<>(messages))
            .updatedAt(Instant.now())
            .build());
    }

    public void persistUserMessage(UserMessage userMessage) {
        messageRepository.save(userMessage);
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistSuccess(AgentRunContext runContext, LoopExecutionResult loopExecutionResult) {
        if (!CollectionUtils.isEmpty(loopExecutionResult.getAppendedMessages())) {
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

        runEventRepository.saveBatch(buildSuccessRunEvents(runContext, loopExecutionResult));

        String conversationId = conversation.getConversationId();
        String sessionId = session.getSessionId();
        List<Message> workingMessages = new ArrayList<>(runContext.getWorkingMessages());
        registerAfterCommit(() -> {
            try {
                refresh(conversationId, sessionId, workingMessages);
            } catch (Exception ex) {
                log.warn("Refresh redis session context failed, sessionId={}", sessionId, ex);
                safeEvictSessionContext(sessionId);
            }
        });
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistFailure(AgentRunContext runContext, String errorCode, String errorMessage) {
        Turn turn = runContext.getTurn();
        turn.markFailed(errorCode, errorMessage, Instant.now());
        turnRepository.update(turn);

        Session session = runContext.getSession();
        session.touch(Instant.now());
        sessionRepository.update(session);

        runEventRepository.saveBatch(List.of(buildRunFailedEvent(runContext, errorCode, errorMessage)));
        registerAfterCommit(() -> safeEvictSessionContext(session.getSessionId()));
    }

    private List<Message> reloadFromMysql(String conversationId, String sessionId) {
        List<Message> completedMessages = messageRepository.findCompletedContextBySessionId(sessionId, maxMessages);
        refresh(conversationId, sessionId, completedMessages);
        return completedMessages;
    }

    private List<RunEventRecord> buildSuccessRunEvents(AgentRunContext runContext, LoopExecutionResult loopExecutionResult) {
        List<RunEventRecord> runEvents = new ArrayList<>();
        int eventIndex = 1;

        if (loopExecutionResult != null && !CollectionUtils.isEmpty(loopExecutionResult.getToolCalls())) {
            for (AssistantToolCall toolCall : loopExecutionResult.getToolCalls()) {
                runEvents.add(RunEventRecord.builder()
                    .eventId(runIdentityFactory.nextRunEventId())
                    .conversationId(runContext.getConversation().getConversationId())
                    .sessionId(runContext.getSession().getSessionId())
                    .turnId(runContext.getTurn().getTurnId())
                    .runId(runContext.getRunMetadata().getRunId())
                    .eventIndex(eventIndex++)
                    .eventType(RunEventType.TOOL_CALL_CREATED)
                    .actorType("assistant")
                    .actorId(toolCall == null ? null : toolCall.getAssistantMessageId())
                    .payloadJson(toolCall == null ? "{}" : JsonUtils.toJson(toolCall))
                    .createdAt(Instant.now())
                    .build());
            }
        }

        if (loopExecutionResult != null && !CollectionUtils.isEmpty(loopExecutionResult.getToolExecutions())) {
            for (ToolExecution toolExecution : loopExecutionResult.getToolExecutions()) {
                RunEventType runEventType = toolExecution != null && toolExecution.getStatus() == ToolExecutionStatus.SUCCESS
                    ? RunEventType.TOOL_COMPLETED
                    : RunEventType.TOOL_FAILED;
                runEvents.add(RunEventRecord.builder()
                    .eventId(runIdentityFactory.nextRunEventId())
                    .conversationId(runContext.getConversation().getConversationId())
                    .sessionId(runContext.getSession().getSessionId())
                    .turnId(runContext.getTurn().getTurnId())
                    .runId(runContext.getRunMetadata().getRunId())
                    .eventIndex(eventIndex++)
                    .eventType(runEventType)
                    .actorType("tool")
                    .actorId(toolExecution == null ? null : toolExecution.getToolExecutionId())
                    .payloadJson(toolExecution == null ? "{}" : JsonUtils.toJson(toolExecution))
                    .createdAt(Instant.now())
                    .build());
            }
        }

        runEvents.add(RunEventRecord.builder()
            .eventId(runIdentityFactory.nextRunEventId())
            .conversationId(runContext.getConversation().getConversationId())
            .sessionId(runContext.getSession().getSessionId())
            .turnId(runContext.getTurn().getTurnId())
            .runId(runContext.getRunMetadata().getRunId())
            .eventIndex(eventIndex)
            .eventType(RunEventType.RUN_COMPLETED)
            .actorType("runtime")
            .actorId(runContext.getRunMetadata().getRunId())
            .payloadJson(JsonUtils.toJson(Map.of(
                "finishReason", loopExecutionResult == null || loopExecutionResult.getFinishReason() == null
                    ? null : loopExecutionResult.getFinishReason().name(),
                "usage", loopExecutionResult == null ? null : loopExecutionResult.getUsage()
            )))
            .createdAt(Instant.now())
            .build());

        return runEvents;
    }

    private RunEventRecord buildRunFailedEvent(AgentRunContext runContext, String errorCode, String errorMessage) {
        return RunEventRecord.builder()
            .eventId(runIdentityFactory.nextRunEventId())
            .conversationId(runContext.getConversation().getConversationId())
            .sessionId(runContext.getSession().getSessionId())
            .turnId(runContext.getTurn().getTurnId())
            .runId(runContext.getRunMetadata().getRunId())
            .eventIndex(1)
            .eventType(RunEventType.RUN_FAILED)
            .actorType("runtime")
            .actorId(runContext.getRunMetadata().getRunId())
            .payloadJson(JsonUtils.toJson(Map.of(
                "errorCode", errorCode,
                "errorMessage", errorMessage
            )))
            .createdAt(Instant.now())
            .build();
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

    private void safeEvictSessionContext(String sessionId) {
        try {
            sessionStateRepository.evict(sessionId);
        } catch (Exception ex) {
            log.warn("Evict redis session context failed, sessionId={}", sessionId, ex);
        }
    }
}

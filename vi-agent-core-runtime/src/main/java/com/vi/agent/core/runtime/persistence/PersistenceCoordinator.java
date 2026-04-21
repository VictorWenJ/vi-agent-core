package com.vi.agent.core.runtime.persistence;

import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.llm.ModelToolCall;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.MessageType;
import com.vi.agent.core.model.message.ToolCallMessage;
import com.vi.agent.core.model.message.ToolResultMessage;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.*;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.session.SessionStateSnapshot;
import com.vi.agent.core.model.tool.ToolCallRecord;
import com.vi.agent.core.model.tool.ToolResultRecord;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.model.turn.TurnStatus;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Coordinates persistence writes for one turn.
 */
@Service
public class PersistenceCoordinator {

    @Resource
    private MessageRepository messageRepository;

    @Resource
    private ToolExecutionRepository toolExecutionRepository;

    @Resource
    private TurnRepository turnRepository;

    @Resource
    private SessionRepository sessionRepository;

    @Resource
    private ConversationRepository conversationRepository;

    @Resource
    private SessionStateRepository sessionStateRepository;

    @Value("${vi.agent.runtime.session-state-window:200}")
    private int maxWindow;

    public List<Message> load(String conversationId, String sessionId) {
        List<Message> messages = sessionStateRepository.findBySessionId(sessionId)
            .map(SessionStateSnapshot::getMessages)
            .orElseGet(() -> reloadFromMysql(conversationId, sessionId));
        List<Message> completedTurnMessages = filterCompletedTurnMessages(messages);
        if (completedTurnMessages.size() != messages.size()) {
            refresh(conversationId, sessionId, completedTurnMessages);
        }
        return buildCompleteData(completedTurnMessages);
    }

    private List<Message> buildCompleteData(List<Message> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return new ArrayList<>();
        }

        List<Message> orderedMessages = messages.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingLong(Message::getSequenceNo))
            .toList();
        if (CollectionUtils.isEmpty(orderedMessages)) {
            return new ArrayList<>();
        }

        Map<String, List<ToolCallContext>> turnToolCalls = buildToolCallContextMap(orderedMessages);
        Map<String, ToolCallRecord> toolCallByMessageId = flattenToolCallByMessageId(turnToolCalls);
        List<Message> completedMessages = new ArrayList<>(orderedMessages.size());

        for (int index = 0; index < orderedMessages.size(); index++) {
            Message message = orderedMessages.get(index);
            if (message == null || message.getMessageType() == null) {
                continue;
            }
            completedMessages.add(switch (message.getMessageType()) {
                case USER_INPUT -> UserMessage.restore(
                    message.getMessageId(),
                    message.getTurnId(),
                    message.getSequenceNo(),
                    message.getContent(),
                    message.getCreatedAt()
                );
                case ASSISTANT_OUTPUT -> AssistantMessage.restore(
                    message.getMessageId(),
                    message.getTurnId(),
                    message.getSequenceNo(),
                    message.getContent(),
                    resolveAssistantToolCalls(message, index, orderedMessages, turnToolCalls),
                    message.getCreatedAt()
                );
                case TOOL_CALL -> toCompletedToolCallMessage(message, toolCallByMessageId);
                case TOOL_RESULT -> toCompletedToolResultMessage(message);
                case SYSTEM_MESSAGE, SUMMARY_MESSAGE -> message;
            });
        }
        return completedMessages;
    }

    private Map<String, List<ToolCallContext>> buildToolCallContextMap(List<Message> orderedMessages) {
        Map<String, Message> messageById = orderedMessages.stream()
            .collect(Collectors.toMap(Message::getMessageId, message -> message, (left, right) -> left));
        Map<String, List<ToolCallContext>> turnToolCalls = new HashMap<>();
        List<String> turnIds = orderedMessages.stream()
            .map(Message::getTurnId)
            .filter(StringUtils::isNotBlank)
            .distinct()
            .toList();

        for (String turnId : turnIds) {
            List<ToolCallRecord> toolCallRecords = toolExecutionRepository.findToolCallsByTurnId(turnId);
            if (CollectionUtils.isEmpty(toolCallRecords)) {
                continue;
            }
            List<ToolCallContext> contexts = toolCallRecords.stream()
                .map(record -> toToolCallContext(record, messageById))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(ToolCallContext::messageSequence))
                .toList();
            if (!CollectionUtils.isEmpty(contexts)) {
                turnToolCalls.put(turnId, contexts);
            }
        }
        return turnToolCalls;
    }

    private Map<String, ToolCallRecord> flattenToolCallByMessageId(Map<String, List<ToolCallContext>> turnToolCalls) {
        Map<String, ToolCallRecord> result = new HashMap<>();
        turnToolCalls.values().stream()
            .flatMap(List::stream)
            .forEach(context -> result.put(context.record().getMessageId(), context.record()));
        return result;
    }

    private ToolCallContext toToolCallContext(ToolCallRecord record, Map<String, Message> messageById) {
        if (record == null || StringUtils.isBlank(record.getMessageId())) {
            return null;
        }
        Message toolCallMessage = messageById.get(record.getMessageId());
        if (toolCallMessage == null) {
            toolCallMessage = messageRepository.findByMessageId(record.getMessageId());
        }
        if (toolCallMessage == null) {
            return null;
        }
        return new ToolCallContext(record, toolCallMessage.getSequenceNo());
    }

    private List<ModelToolCall> resolveAssistantToolCalls(
        Message assistantMessage,
        int assistantIndex,
        List<Message> orderedMessages,
        Map<String, List<ToolCallContext>> turnToolCalls
    ) {
        List<ToolCallContext> contexts = turnToolCalls.get(assistantMessage.getTurnId());
        if (CollectionUtils.isEmpty(contexts)) {
            return List.of();
        }

        long currentSequence = assistantMessage.getSequenceNo();
        long nextAssistantSequence = Long.MAX_VALUE;
        for (int index = assistantIndex + 1; index < orderedMessages.size(); index++) {
            Message nextMessage = orderedMessages.get(index);
            if (!StringUtils.equals(assistantMessage.getTurnId(), nextMessage.getTurnId())) {
                continue;
            }
            if (nextMessage.getMessageType() == MessageType.ASSISTANT_OUTPUT) {
                nextAssistantSequence = nextMessage.getSequenceNo();
                break;
            }
        }
        long nextAssistantSequenceBoundary = nextAssistantSequence;

        return contexts.stream()
            .filter(context -> context.messageSequence() > currentSequence && context.messageSequence() < nextAssistantSequenceBoundary)
            .map(context -> ModelToolCall.builder()
                .toolCallId(context.record().getToolCallId())
                .toolName(context.record().getToolName())
                .argumentsJson(context.record().getArgumentsJson())
                .build())
            .toList();
    }

    private Message toCompletedToolCallMessage(Message message, Map<String, ToolCallRecord> toolCallByMessageId) {
        ToolCallRecord record = toolCallByMessageId.get(message.getMessageId());
        if (record == null) {
            record = toolExecutionRepository.findToolCallByMessageId(message.getMessageId());
        }
        if (record == null) {
            return message;
        }
        return ToolCallMessage.restore(
            message.getMessageId(),
            message.getTurnId(),
            message.getSequenceNo(),
            record.getToolCallId(),
            record.getToolName(),
            record.getArgumentsJson(),
            message.getCreatedAt()
        );
    }

    private Message toCompletedToolResultMessage(Message message) {
        ToolResultRecord record = toolExecutionRepository.findToolResultByMessageId(message.getMessageId());
        if (record == null) {
            return message;
        }
        return ToolResultMessage.restore(
            message.getMessageId(),
            message.getTurnId(),
            message.getSequenceNo(),
            record.getToolCallId(),
            record.getToolName(),
            record.isSuccess(),
            record.getOutputJson(),
            record.getErrorCode(),
            record.getErrorMessage(),
            record.getDurationMs(),
            message.getCreatedAt()
        );
    }

    private record ToolCallContext(ToolCallRecord record, long messageSequence) {
    }

    public void refresh(String conversationId, String sessionId, List<Message> messages) {
        sessionStateRepository.save(SessionStateSnapshot.builder()
            .sessionId(sessionId)
            .conversationId(conversationId)
            .messages(new ArrayList<>(messages))
            .updatedAt(Instant.now())
            .build());
    }

    private List<Message> reloadFromMysql(String conversationId, String sessionId) {
        List<Message> messages = messageRepository.findBySessionIdOrderBySequence(sessionId, maxWindow);
        List<Message> completedTurnMessages = filterCompletedTurnMessages(messages);
        refresh(conversationId, sessionId, completedTurnMessages);
        return completedTurnMessages;
    }

    private List<Message> filterCompletedTurnMessages(List<Message> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return new ArrayList<>();
        }

        return messages.stream()
            .filter(message -> turnRepository.findByTurnId(message.getTurnId()).getStatus() == TurnStatus.COMPLETED)
            .collect(Collectors.toCollection(ArrayList::new));
    }

    public void persistUserMessage(String conversationId, String sessionId, Message userMessage) {
        messageRepository.save(conversationId, sessionId, userMessage);
    }

    public void persistSuccess(AgentRunContext runContext, LoopExecutionResult loopExecutionResult) {
        loopExecutionResult.getAppendedMessages()
            .forEach(message -> messageRepository.save(runContext.getConversation().getConversationId(), runContext.getSession().getSessionId(), message));

        loopExecutionResult.getToolCalls().forEach(toolExecutionRepository::saveToolCall);
        loopExecutionResult.getToolResults().forEach(toolExecutionRepository::saveToolResult);

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

        sessionStateRepository.save(SessionStateSnapshot.builder()
            .sessionId(session.getSessionId())
            .conversationId(conversation.getConversationId())
            .messages(runContext.getWorkingMessages())
            .updatedAt(Instant.now())
            .build());
    }

    public void persistFailure(AgentRunContext runContext, String errorCode, String errorMessage) {
        Turn turn = runContext.getTurn();
        turn.markFailed(errorCode, errorMessage, Instant.now());
        turnRepository.update(turn);

        Session session = runContext.getSession();
        session.touch(Instant.now());
        sessionRepository.update(session);

        sessionStateRepository.evict(session.getSessionId());
    }
}

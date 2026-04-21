package com.vi.agent.core.runtime.persistence;

import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.port.*;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.session.SessionStateSnapshot;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.model.turn.TurnStatus;
import com.vi.agent.core.runtime.context.ModelContextMessageFilter;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Resource
    private ModelContextMessageFilter modelContextMessageFilter;

    @Value("${vi.agent.runtime.session-state-window:200}")
    private int maxWindow;

    public List<Message> load(String conversationId, String sessionId) {
        List<Message> messages = sessionStateRepository.findBySessionId(sessionId)
            .map(SessionStateSnapshot::getMessages)
            .orElseGet(() -> reloadFromMysql(conversationId, sessionId));
        List<Message> completedTurnMessages = filterCompletedTurnMessages(messages);
        List<Message> modelContextMessages = modelContextMessageFilter.filter(completedTurnMessages);
        if (modelContextMessages.size() != messages.size()) {
            refresh(conversationId, sessionId, modelContextMessages);
        }
        return new ArrayList<>(modelContextMessages);
    }

    public void refresh(String conversationId, String sessionId, List<Message> messages) {
        List<Message> modelContextMessages = modelContextMessageFilter.filter(messages);
        sessionStateRepository.save(SessionStateSnapshot.builder()
            .sessionId(sessionId)
            .conversationId(conversationId)
            .messages(new ArrayList<>(modelContextMessages))
            .updatedAt(Instant.now())
            .build());
    }

    private List<Message> reloadFromMysql(String conversationId, String sessionId) {
        List<Message> messages = messageRepository.findBySessionIdOrderBySequence(sessionId, maxWindow);
        List<Message> completedTurnMessages = filterCompletedTurnMessages(messages);
        List<Message> modelContextMessages = modelContextMessageFilter.filter(completedTurnMessages);
        refresh(conversationId, sessionId, modelContextMessages);
        return modelContextMessages;
    }

    private List<Message> filterCompletedTurnMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, Boolean> completedTurnCache = new HashMap<>();
        return messages.stream()
            .filter(message -> completedTurnCache.computeIfAbsent(message.getTurnId(), this::isCompletedTurn))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private boolean isCompletedTurn(String turnId) {
        if (turnId == null || turnId.isBlank()) {
            return false;
        }
        return turnRepository.findByTurnId(turnId)
            .map(turn -> turn.getStatus() == TurnStatus.COMPLETED)
            .orElse(false);
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

        var modelContextMessages = modelContextMessageFilter.filter(runContext.getWorkingMessages());

        sessionStateRepository.save(SessionStateSnapshot.builder()
            .sessionId(session.getSessionId())
            .conversationId(conversation.getConversationId())
            .messages(modelContextMessages)
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

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
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
        return messages.stream()
            .map(message -> {
              switch (message.getMessageType()) {
                  case USER_INPUT:
                  case TOOL_CALL:
                  case TOOL_RESULT:
                  case SYSTEM_MESSAGE:
                  case SUMMARY_MESSAGE:
                  case ASSISTANT_OUTPUT:
              }
            }).toList();
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

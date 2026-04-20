package com.vi.agent.core.runtime.persistence;

import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.port.*;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.turn.Turn;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.Instant;

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

    public void persistUserMessage(String conversationId, String sessionId, Message userMessage) {
        messageRepository.save(conversationId, sessionId, userMessage);
    }

    public void persistSuccess(AgentRunContext runContext, LoopExecutionResult loopExecutionResult) {
        for (Message message : loopExecutionResult.getAppendedMessages()) {
            messageRepository.save(
                runContext.getConversation().getConversationId(),
                runContext.getSession().getSessionId(),
                message
            );
        }
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

        sessionStateRepository.save(com.vi.agent.core.model.session.SessionStateSnapshot.builder()
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
        session.markFailed();
        sessionRepository.update(session);

        sessionStateRepository.evict(session.getSessionId());
    }
}

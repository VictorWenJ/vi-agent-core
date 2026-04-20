package com.vi.agent.core.runtime.session;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.conversation.ConversationStatus;
import com.vi.agent.core.model.port.ConversationRepository;
import com.vi.agent.core.model.port.SessionRepository;
import com.vi.agent.core.model.session.*;
import com.vi.agent.core.runtime.command.RuntimeExecuteCommand;
import com.vi.agent.core.runtime.factory.RunIdentityFactory;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;

/**
 * Default session resolution service.
 */
@Service
public class DefaultSessionResolutionService implements SessionResolutionService {

    @Resource
    private ConversationRepository conversationRepository;

    @Resource
    private SessionRepository sessionRepository;

    @Resource
    private RunIdentityFactory runIdentityFactory;

    @Override
    public SessionResolutionResult resolve(RuntimeExecuteCommand command) {
        if (command.getSessionMode() == null) {
            throw new AgentRuntimeException(ErrorCode.SESSION_MODE_INVALID, "sessionMode is required");
        }
        return switch (command.getSessionMode()) {
            case NEW_CONVERSATION -> resolveNewConversation(command);
            case CONTINUE_ACTIVE_SESSION -> resolveContinueActive(command);
            case CONTINUE_EXACT_SESSION -> resolveContinueExact(command);
            case START_NEW_SESSION -> resolveStartNewSession(command);
        };
    }

    private SessionResolutionResult resolveNewConversation(RuntimeExecuteCommand command) {
        if (command.getConversationId() != null || command.getSessionId() != null) {
            throw new AgentRuntimeException(ErrorCode.SESSION_MODE_INVALID,
                "NEW_CONVERSATION requires conversationId/sessionId to be empty");
        }
        Instant now = Instant.now();
        String conversationId = runIdentityFactory.nextConversationId();
        String sessionId = runIdentityFactory.nextSessionId();

        Conversation conversation = Conversation.builder()
            .conversationId(conversationId)
            .title(null)
            .status(ConversationStatus.ACTIVE)
            .activeSessionId(sessionId)
            .createdAt(now)
            .updatedAt(now)
            .lastMessageAt(now)
            .build();
        conversationRepository.save(conversation);

        Session session = Session.builder()
            .sessionId(sessionId)
            .conversationId(conversationId)
            .parentSessionId(null)
            .status(SessionStatus.ACTIVE)
            .archiveReason(null)
            .createdAt(now)
            .updatedAt(now)
            .archivedAt(null)
            .build();
        sessionRepository.save(session);

        return SessionResolutionResult.builder()
            .conversation(conversation)
            .session(session)
            .createdConversation(true)
            .createdSession(true)
            .build();
    }

    private SessionResolutionResult resolveContinueActive(RuntimeExecuteCommand command) {
        if (StringUtils.isBlank(command.getConversationId()) || StringUtils.isNotBlank(command.getSessionId())) {
            throw new AgentRuntimeException(ErrorCode.SESSION_MODE_INVALID, "CONTINUE_ACTIVE_SESSION requires conversationId and empty sessionId");
        }

        Conversation conversation = conversationRepository.findByConversationId(command.getConversationId())
            .orElseThrow(() -> new AgentRuntimeException(ErrorCode.CONVERSATION_NOT_FOUND, "conversation not found"));
        if (StringUtils.isBlank(conversation.getActiveSessionId())) {
            throw new AgentRuntimeException(ErrorCode.SESSION_NOT_FOUND, "active session not found");
        }

        Session session = sessionRepository.findBySessionId(conversation.getActiveSessionId())
            .orElseThrow(() -> new AgentRuntimeException(ErrorCode.SESSION_NOT_FOUND, "active session not found"));

        return SessionResolutionResult.builder()
            .conversation(conversation)
            .session(session)
            .createdConversation(false)
            .createdSession(false)
            .build();
    }

    private SessionResolutionResult resolveContinueExact(RuntimeExecuteCommand command) {
        if (StringUtils.isBlank(command.getConversationId()) || StringUtils.isBlank(command.getSessionId())) {
            throw new AgentRuntimeException(ErrorCode.SESSION_MODE_INVALID,
                "CONTINUE_EXACT_SESSION requires conversationId and sessionId");
        }

        Conversation conversation = conversationRepository.findByConversationId(command.getConversationId())
            .orElseThrow(() -> new AgentRuntimeException(ErrorCode.CONVERSATION_NOT_FOUND, "conversation not found"));

        Session session = sessionRepository.findBySessionId(command.getSessionId())
            .orElseThrow(() -> new AgentRuntimeException(ErrorCode.SESSION_NOT_FOUND, "session not found"));

        if (!Objects.equals(session.getConversationId(), conversation.getConversationId())) {
            throw new AgentRuntimeException(ErrorCode.SESSION_CONVERSATION_MISMATCH, "session does not belong to conversation");
        }

        return SessionResolutionResult.builder()
            .conversation(conversation)
            .session(session)
            .createdConversation(false)
            .createdSession(false)
            .build();
    }

    private SessionResolutionResult resolveStartNewSession(RuntimeExecuteCommand command) {
        if (StringUtils.isBlank(command.getConversationId()) || StringUtils.isNotBlank(command.getSessionId())) {
            throw new AgentRuntimeException(ErrorCode.SESSION_MODE_INVALID, "START_NEW_SESSION requires conversationId and empty sessionId");
        }

        Instant now = Instant.now();
        Conversation conversation = conversationRepository.findByConversationId(command.getConversationId())
            .orElseThrow(() -> new AgentRuntimeException(ErrorCode.CONVERSATION_NOT_FOUND, "conversation not found"));

        Session parent = null;
        if (StringUtils.isNotBlank(conversation.getActiveSessionId())) {
            parent = sessionRepository.findBySessionId(conversation.getActiveSessionId()).orElse(null);
            if (Objects.nonNull(parent) && parent.getStatus() == SessionStatus.ACTIVE) {
                parent.archive("START_NEW_SESSION", now);
                sessionRepository.update(parent);
            }
        }

        String newSessionId = runIdentityFactory.nextSessionId();
        Session newSession = Session.builder()
            .sessionId(newSessionId)
            .conversationId(conversation.getConversationId())
            .parentSessionId(parent == null ? null : parent.getSessionId())
            .status(SessionStatus.ACTIVE)
            .archiveReason(null)
            .createdAt(now)
            .updatedAt(now)
            .archivedAt(null)
            .build();
        sessionRepository.save(newSession);

        conversation.activateSession(newSessionId);
        conversationRepository.update(conversation);

        return SessionResolutionResult.builder()
            .conversation(conversation)
            .session(newSession)
            .createdConversation(false)
            .createdSession(true)
            .build();
    }
}

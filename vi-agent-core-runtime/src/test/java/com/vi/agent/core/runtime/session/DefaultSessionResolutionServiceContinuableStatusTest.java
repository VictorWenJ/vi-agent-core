package com.vi.agent.core.runtime.session;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.conversation.ConversationStatus;
import com.vi.agent.core.model.port.ConversationRepository;
import com.vi.agent.core.model.port.SessionRepository;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.session.SessionMode;
import com.vi.agent.core.model.session.SessionResolutionResult;
import com.vi.agent.core.model.session.SessionStatus;
import com.vi.agent.core.runtime.command.RuntimeExecuteCommand;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultSessionResolutionServiceContinuableStatusTest {

    @Test
    void continueExactShouldRejectArchivedSession() throws Exception {
        DefaultSessionResolutionService service = new DefaultSessionResolutionService();
        StubConversationRepository conversationRepository = new StubConversationRepository();
        StubSessionRepository sessionRepository = new StubSessionRepository();
        setField(service, "conversationRepository", conversationRepository);
        setField(service, "sessionRepository", sessionRepository);

        conversationRepository.conversation = activeConversation("conv-1", "sess-1");
        sessionRepository.session = session("sess-1", "conv-1", SessionStatus.ARCHIVED);

        RuntimeExecuteCommand command = RuntimeExecuteCommand.builder()
            .requestId("req-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .sessionMode(SessionMode.CONTINUE_EXACT_SESSION)
            .message("hello")
            .build();

        AgentRuntimeException ex = assertThrows(AgentRuntimeException.class, () -> service.resolve(command));
        assertEquals(ErrorCode.SESSION_ARCHIVED_NOT_CONTINUABLE, ex.getErrorCode());
    }

    @Test
    void continueExactShouldRejectFailedSession() throws Exception {
        DefaultSessionResolutionService service = new DefaultSessionResolutionService();
        StubConversationRepository conversationRepository = new StubConversationRepository();
        StubSessionRepository sessionRepository = new StubSessionRepository();
        setField(service, "conversationRepository", conversationRepository);
        setField(service, "sessionRepository", sessionRepository);

        conversationRepository.conversation = activeConversation("conv-1", "sess-1");
        sessionRepository.session = session("sess-1", "conv-1", SessionStatus.FAILED);

        RuntimeExecuteCommand command = RuntimeExecuteCommand.builder()
            .requestId("req-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .sessionMode(SessionMode.CONTINUE_EXACT_SESSION)
            .message("hello")
            .build();

        AgentRuntimeException ex = assertThrows(AgentRuntimeException.class, () -> service.resolve(command));
        assertEquals(ErrorCode.SESSION_FAILED_NOT_CONTINUABLE, ex.getErrorCode());
    }

    @Test
    void continueExactShouldAllowActiveSession() throws Exception {
        DefaultSessionResolutionService service = new DefaultSessionResolutionService();
        StubConversationRepository conversationRepository = new StubConversationRepository();
        StubSessionRepository sessionRepository = new StubSessionRepository();
        setField(service, "conversationRepository", conversationRepository);
        setField(service, "sessionRepository", sessionRepository);

        conversationRepository.conversation = activeConversation("conv-1", "sess-1");
        sessionRepository.session = session("sess-1", "conv-1", SessionStatus.ACTIVE);

        RuntimeExecuteCommand command = RuntimeExecuteCommand.builder()
            .requestId("req-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .sessionMode(SessionMode.CONTINUE_EXACT_SESSION)
            .message("hello")
            .build();

        SessionResolutionResult result = service.resolve(command);
        assertNotNull(result);
        assertEquals("sess-1", result.getSession().getSessionId());
        assertEquals(SessionStatus.ACTIVE, result.getSession().getStatus());
    }

    private static Conversation activeConversation(String conversationId, String activeSessionId) {
        return Conversation.builder()
            .conversationId(conversationId)
            .title("title")
            .status(ConversationStatus.ACTIVE)
            .activeSessionId(activeSessionId)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .lastMessageAt(Instant.now())
            .build();
    }

    private static Session session(String sessionId, String conversationId, SessionStatus status) {
        return Session.builder()
            .sessionId(sessionId)
            .conversationId(conversationId)
            .status(status)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = DefaultSessionResolutionService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class StubConversationRepository implements ConversationRepository {
        private Conversation conversation;

        @Override
        public Optional<Conversation> findByConversationId(String conversationId) {
            return Optional.ofNullable(conversation);
        }

        @Override
        public void save(Conversation conversation) {
            this.conversation = conversation;
        }

        @Override
        public void update(Conversation conversation) {
            this.conversation = conversation;
        }
    }

    private static final class StubSessionRepository implements SessionRepository {
        private Session session;

        @Override
        public Optional<Session> findBySessionId(String sessionId) {
            return Optional.ofNullable(session);
        }

        @Override
        public Optional<Session> findActiveByConversationId(String conversationId) {
            if (session != null && session.getStatus() == SessionStatus.ACTIVE) {
                return Optional.of(session);
            }
            return Optional.empty();
        }

        @Override
        public void save(Session session) {
            this.session = session;
        }

        @Override
        public void update(Session session) {
            this.session = session;
        }
    }
}


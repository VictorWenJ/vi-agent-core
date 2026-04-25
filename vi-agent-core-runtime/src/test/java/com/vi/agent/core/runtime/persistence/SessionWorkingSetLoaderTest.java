package com.vi.agent.core.runtime.persistence;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.port.SessionWorkingSetRepository;
import com.vi.agent.core.model.memory.SessionWorkingSetSnapshot;
import com.vi.agent.core.runtime.support.TestFieldUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionWorkingSetLoaderTest {

    @Test
    void shouldLoadFromMysqlWithConfiguredMaxTurnsAndCache() {
        SessionWorkingSetLoader loader = new SessionWorkingSetLoader();
        StubMessageRepository messageRepository = new StubMessageRepository();
        StubSessionWorkingSetRepository sessionWorkingSetRepository = new StubSessionWorkingSetRepository();

        TestFieldUtils.setField(loader, "messageRepository", messageRepository);
        TestFieldUtils.setField(loader, "sessionWorkingSetRepository", sessionWorkingSetRepository);
        TestFieldUtils.setField(loader, "maxCompletedTurns", 3);

        List<Message> result = loader.load("conv-1", "sess-1");

        assertEquals(3, messageRepository.lastMaxTurns);
        assertEquals(1, result.size());
        assertEquals(1, sessionWorkingSetRepository.saveCount);
        assertEquals("sess-1", sessionWorkingSetRepository.lastSaved.getSessionId());
    }

    @Test
    void shouldUseCacheWhenSnapshotExists() {
        SessionWorkingSetLoader loader = new SessionWorkingSetLoader();
        StubMessageRepository messageRepository = new StubMessageRepository();
        StubSessionWorkingSetRepository sessionWorkingSetRepository = new StubSessionWorkingSetRepository();

        SessionWorkingSetSnapshot cached = SessionWorkingSetSnapshot.builder()
            .sessionId("sess-1")
            .conversationId("conv-1")
            .messages(List.of(UserMessage.create("msg-1", "conv-1", "sess-1", "turn-1", "run-1", 1L, "cached")))
            .updatedAt(Instant.now())
            .build();
        sessionWorkingSetRepository.cached = cached;

        TestFieldUtils.setField(loader, "messageRepository", messageRepository);
        TestFieldUtils.setField(loader, "sessionWorkingSetRepository", sessionWorkingSetRepository);
        TestFieldUtils.setField(loader, "maxCompletedTurns", 2);

        List<Message> result = loader.load("conv-1", "sess-1");

        assertEquals(0, messageRepository.findCount);
        assertEquals(1, result.size());
        assertEquals("cached", result.get(0).getContent());
    }

    private static final class StubMessageRepository implements MessageRepository {
        private int lastMaxTurns;
        private int findCount;

        @Override
        public void saveBatch(List<Message> messages) {
        }

        @Override
        public Message findByMessageId(String messageId) {
            return null;
        }

        @Override
        public List<Message> findCompletedContextBySessionId(String sessionId, int maxTurns) {
            findCount++;
            lastMaxTurns = maxTurns;
            return List.of(UserMessage.create("msg-1", "conv-1", sessionId, "turn-1", "run-1", 1L, "mysql"));
        }

        @Override
        public List<Message> findByTurnId(String turnId) {
            return List.of();
        }

        @Override
        public Message findFinalAssistantMessageByTurnId(String turnId) {
            return null;
        }

        @Override
        public long nextSequenceNo(String sessionId) {
            return 1L;
        }
    }

    private static final class StubSessionWorkingSetRepository implements SessionWorkingSetRepository {
        private SessionWorkingSetSnapshot cached;
        private int saveCount;
        private SessionWorkingSetSnapshot lastSaved;

        @Override
        public SessionWorkingSetSnapshot findBySessionId(String sessionId) {
            return cached;
        }

        @Override
        public void save(SessionWorkingSetSnapshot snapshot) {
            saveCount++;
            lastSaved = snapshot;
        }

        @Override
        public void evict(String sessionId) {
        }
    }
}


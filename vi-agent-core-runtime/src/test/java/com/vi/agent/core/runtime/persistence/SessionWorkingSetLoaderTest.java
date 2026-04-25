package com.vi.agent.core.runtime.persistence;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.memory.SessionWorkingSetSnapshot;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.port.SessionWorkingSetRepository;
import com.vi.agent.core.runtime.support.TestFieldUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SessionWorkingSetLoaderTest {

    @Test
    void shouldLoadFromMysqlWithConfiguredMaxTurnsAndSaveWorkingSet() {
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
        assertEquals(1L, sessionWorkingSetRepository.lastSaved.getWorkingSetVersion());
        assertEquals(3, sessionWorkingSetRepository.lastSaved.getMaxCompletedTurns());
        assertEquals(1L, sessionWorkingSetRepository.lastSaved.getSummaryCoveredToSequenceNo());
        assertEquals(List.of("msg-1"), sessionWorkingSetRepository.lastSaved.getRawMessageIds());
    }

    @Test
    void shouldUseCacheWhenSnapshotExists() {
        SessionWorkingSetLoader loader = new SessionWorkingSetLoader();
        StubMessageRepository messageRepository = new StubMessageRepository();
        StubSessionWorkingSetRepository sessionWorkingSetRepository = new StubSessionWorkingSetRepository();

        SessionWorkingSetSnapshot cached = SessionWorkingSetSnapshot.builder()
            .sessionId("sess-1")
            .conversationId("conv-1")
            .workingSetVersion(2L)
            .maxCompletedTurns(5)
            .summaryCoveredToSequenceNo(99L)
            .rawMessageId("msg-cached")
            .message(UserMessage.create("msg-cached", "conv-1", "sess-1", "turn-1", "run-1", 1L, "cached"))
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
        assertEquals(0, sessionWorkingSetRepository.saveCount);
        assertEquals(2L, sessionWorkingSetRepository.cached.getWorkingSetVersion());
    }

    @Test
    void refreshFromMysqlShouldContinueVersionOnCacheMissAndUpdateFields() {
        SessionWorkingSetLoader loader = new SessionWorkingSetLoader();
        StubMessageRepository messageRepository = new StubMessageRepository();
        StubSessionWorkingSetRepository sessionWorkingSetRepository = new StubSessionWorkingSetRepository();
        sessionWorkingSetRepository.cached = SessionWorkingSetSnapshot.builder()
            .sessionId("sess-1")
            .conversationId("conv-1")
            .workingSetVersion(4L)
            .maxCompletedTurns(3)
            .summaryCoveredToSequenceNo(9L)
            .rawMessageId("msg-old")
            .message(UserMessage.create("msg-old", "conv-1", "sess-1", "turn-old", "run-1", 1L, "old"))
            .updatedAt(Instant.now())
            .build();

        TestFieldUtils.setField(loader, "messageRepository", messageRepository);
        TestFieldUtils.setField(loader, "sessionWorkingSetRepository", sessionWorkingSetRepository);
        TestFieldUtils.setField(loader, "maxCompletedTurns", 3);

        SessionWorkingSetSnapshot snapshot = loader.refreshFromMysql("conv-1", "sess-1");

        assertNotNull(snapshot);
        assertEquals(5L, snapshot.getWorkingSetVersion());
        assertEquals(3, snapshot.getMaxCompletedTurns());
        assertEquals(List.of("msg-1"), snapshot.getRawMessageIds());
        assertEquals(1L, snapshot.getSummaryCoveredToSequenceNo());
        assertEquals("conv-1", snapshot.getConversationId());
        assertEquals(1, sessionWorkingSetRepository.saveCount);
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
        SessionWorkingSetSnapshot lastSaved;

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

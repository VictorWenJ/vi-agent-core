package com.vi.agent.core.infra.persistence.cache.session.repository;

import com.vi.agent.core.infra.persistence.cache.session.key.SessionRedisKeyBuilder;
import com.vi.agent.core.infra.persistence.cache.session.mapper.SessionWorkingSetRedisMapper;
import com.vi.agent.core.model.memory.SessionWorkingSetSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisSessionWorkingSetRepositoryTest {

    @Test
    void saveShouldWriteHashWithVersionAndWorkingSetFields() {
        RedisSessionWorkingSetRepository repository = buildRepository(60L);
        StringRedisTemplate redisTemplate = getField(repository, "stringRedisTemplate");
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = (HashOperations<String, Object, Object>) redisTemplate.opsForHash();

        SessionWorkingSetSnapshot snapshot = SessionWorkingSetSnapshot.builder()
            .sessionId("sess-1")
            .conversationId("conv-1")
            .workingSetVersion(3L)
            .maxCompletedTurns(5)
            .summaryCoveredToSequenceNo(7L)
            .rawMessageId("msg-1")
            .rawMessageId("msg-2")
            .updatedAt(Instant.parse("2026-04-25T00:00:00Z"))
            .build();

        repository.save(snapshot);

        ArgumentCaptor<Map<String, String>> hashCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOps).putAll(eq("agent:session:working-set:sess-1"), hashCaptor.capture());
        verify(redisTemplate).expire(eq("agent:session:working-set:sess-1"), eq(Duration.ofSeconds(60)));
        Map<String, String> saved = hashCaptor.getValue();
        assertEquals("3", saved.get("workingSetVersion"));
        assertEquals("5", saved.get("maxCompletedTurns"));
        assertEquals("7", saved.get("summaryCoveredToSequenceNo"));
        assertEquals("[\"msg-1\",\"msg-2\"]", saved.get("rawMessageIdsJson"));
        assertEquals("1", saved.get("snapshotVersion"));
        assertEquals(Set.of(
            "sessionId",
            "conversationId",
            "workingSetVersion",
            "maxCompletedTurns",
            "summaryCoveredToSequenceNo",
            "rawMessageIdsJson",
            "snapshotVersion",
            "updatedAtEpochMs"
        ), saved.keySet());
    }

    @Test
    void findShouldEvictWhenSnapshotVersionInvalid() {
        RedisSessionWorkingSetRepository repository = buildRepository(60L);
        StringRedisTemplate redisTemplate = getField(repository, "stringRedisTemplate");
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = (HashOperations<String, Object, Object>) redisTemplate.opsForHash();

        Map<Object, Object> hash = validHash();
        hash.put("snapshotVersion", "2");
        when(hashOps.entries("agent:session:working-set:sess-1")).thenReturn(hash);

        SessionWorkingSetSnapshot result = repository.findBySessionId("sess-1");

        assertNull(result);
        verify(redisTemplate).delete("agent:session:working-set:sess-1");
    }

    @Test
    void findShouldEvictWhenRequiredFieldMissing() {
        RedisSessionWorkingSetRepository repository = buildRepository(60L);
        StringRedisTemplate redisTemplate = getField(repository, "stringRedisTemplate");
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = (HashOperations<String, Object, Object>) redisTemplate.opsForHash();

        Map<Object, Object> hash = validHash();
        hash.remove("workingSetVersion");
        when(hashOps.entries("agent:session:working-set:sess-1")).thenReturn(hash);

        SessionWorkingSetSnapshot result = repository.findBySessionId("sess-1");

        assertNull(result);
        verify(redisTemplate).delete("agent:session:working-set:sess-1");
    }

    @Test
    void findShouldRestoreWorkingSetFieldsWhenVersionMatches() {
        RedisSessionWorkingSetRepository repository = buildRepository(60L);
        StringRedisTemplate redisTemplate = getField(repository, "stringRedisTemplate");
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = (HashOperations<String, Object, Object>) redisTemplate.opsForHash();

        when(hashOps.entries("agent:session:working-set:sess-1")).thenReturn(validHash());

        SessionWorkingSetSnapshot result = repository.findBySessionId("sess-1");

        assertNotNull(result);
        assertEquals("sess-1", result.getSessionId());
        assertEquals(8L, result.getWorkingSetVersion());
        assertEquals(6, result.getMaxCompletedTurns());
        assertEquals(9L, result.getSummaryCoveredToSequenceNo());
        assertEquals(List.of("msg-a", "msg-b"), result.getRawMessageIds());
    }

    private Map<Object, Object> validHash() {
        Map<Object, Object> hash = new HashMap<>();
        hash.put("sessionId", "sess-1");
        hash.put("conversationId", "conv-1");
        hash.put("snapshotVersion", "1");
        hash.put("workingSetVersion", "8");
        hash.put("maxCompletedTurns", "6");
        hash.put("summaryCoveredToSequenceNo", "9");
        hash.put("rawMessageIdsJson", "[\"msg-a\",\"msg-b\"]");
        hash.put("updatedAtEpochMs", "1000");
        return hash;
    }

    private RedisSessionWorkingSetRepository buildRepository(long ttlSeconds) {
        RedisSessionWorkingSetRepository repository = new RedisSessionWorkingSetRepository();
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = Mockito.mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);

        setField(repository, "stringRedisTemplate", redisTemplate);
        setField(repository, "keyBuilder", new SessionRedisKeyBuilder());
        setField(repository, "sessionWorkingSetRedisMapper", new SessionWorkingSetRedisMapper());
        setField(repository, "sessionWorkingSetTtlSeconds", ttlSeconds);
        return repository;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String fieldName) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}

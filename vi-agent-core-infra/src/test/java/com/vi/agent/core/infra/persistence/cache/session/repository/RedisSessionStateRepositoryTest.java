package com.vi.agent.core.infra.persistence.cache.session.repository;

import com.vi.agent.core.infra.persistence.cache.session.key.SessionRedisKeyBuilder;
import com.vi.agent.core.infra.persistence.cache.session.mapper.SessionStateRedisMapper;
import com.vi.agent.core.model.context.WorkingMode;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisSessionStateRepositoryTest {

    @Test
    void saveShouldWriteStateSnapshotHash() {
        RedisSessionStateRepository repository = buildRepository(120L);
        StringRedisTemplate redisTemplate = getField(repository, "stringRedisTemplate");
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = (HashOperations<String, Object, Object>) redisTemplate.opsForHash();

        SessionStateSnapshot snapshot = SessionStateSnapshot.builder()
            .snapshotId("state-1")
            .sessionId("sess-1")
            .stateVersion(3L)
            .taskGoal("finish")
            .workingMode(WorkingMode.TASK_EXECUTION)
            .updatedAt(Instant.parse("2026-04-25T00:00:00Z"))
            .build();

        repository.save(snapshot);

        ArgumentCaptor<Map<String, String>> hashCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOps).putAll(eq("agent:session:state:sess-1"), hashCaptor.capture());
        verify(redisTemplate).expire(eq("agent:session:state:sess-1"), eq(Duration.ofSeconds(120)));
        Map<String, String> saved = hashCaptor.getValue();
        assertEquals("state-1", saved.get("snapshotId"));
        assertEquals("sess-1", saved.get("sessionId"));
        assertEquals("3", saved.get("stateVersion"));
        assertEquals("finish", saved.get("taskGoal"));
        assertEquals("1", saved.get("snapshotVersion"));
    }

    @Test
    void findShouldRestoreStateAndEvictOnlyOnCorruption() {
        RedisSessionStateRepository repository = buildRepository(120L);
        StringRedisTemplate redisTemplate = getField(repository, "stringRedisTemplate");
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = (HashOperations<String, Object, Object>) redisTemplate.opsForHash();

        Map<Object, Object> hash = validHash();
        when(hashOps.entries("agent:session:state:sess-1")).thenReturn(hash);

        SessionStateSnapshot result = repository.findBySessionId("sess-1");

        assertNotNull(result);
        assertEquals("state-1", result.getSnapshotId());
        assertEquals(3L, result.getStateVersion());
        assertEquals(WorkingMode.TASK_EXECUTION, result.getWorkingMode());
    }

    @Test
    void findShouldEvictWhenRequiredFieldMissing() {
        RedisSessionStateRepository repository = buildRepository(120L);
        StringRedisTemplate redisTemplate = getField(repository, "stringRedisTemplate");
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = (HashOperations<String, Object, Object>) redisTemplate.opsForHash();

        Map<Object, Object> hash = validHash();
        hash.remove("stateJson");
        when(hashOps.entries("agent:session:state:sess-1")).thenReturn(hash);

        SessionStateSnapshot result = repository.findBySessionId("sess-1");

        assertNull(result);
        verify(redisTemplate).delete("agent:session:state:sess-1");
    }

    private Map<Object, Object> validHash() {
        SessionStateSnapshot snapshot = SessionStateSnapshot.builder()
            .snapshotId("state-1")
            .sessionId("sess-1")
            .stateVersion(3L)
            .taskGoal("finish")
            .workingMode(WorkingMode.TASK_EXECUTION)
            .updatedAt(Instant.parse("2026-04-25T00:00:00Z"))
            .build();
        SessionStateRedisMapper mapper = new SessionStateRedisMapper();
        Map<Object, Object> hash = new HashMap<>();
        hash.put("snapshotId", "state-1");
        hash.put("sessionId", "sess-1");
        hash.put("stateVersion", "3");
        hash.put("taskGoal", "finish");
        hash.put("stateJson", mapper.toDocument(snapshot).getStateJson());
        hash.put("snapshotVersion", "1");
        hash.put("updatedAtEpochMs", "1777075200000");
        return hash;
    }

    private RedisSessionStateRepository buildRepository(long ttlSeconds) {
        RedisSessionStateRepository repository = new RedisSessionStateRepository();
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = Mockito.mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);

        setField(repository, "stringRedisTemplate", redisTemplate);
        setField(repository, "keyBuilder", new SessionRedisKeyBuilder());
        setField(repository, "sessionStateRedisMapper", new SessionStateRedisMapper());
        setField(repository, "sessionStateTtlSeconds", ttlSeconds);
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

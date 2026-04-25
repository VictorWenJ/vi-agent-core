package com.vi.agent.core.infra.persistence.cache.session.repository;

import com.vi.agent.core.infra.persistence.cache.session.key.SessionRedisKeyBuilder;
import com.vi.agent.core.infra.persistence.cache.session.mapper.SessionWorkingSetRedisMapper;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.memory.SessionWorkingSetSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisSessionWorkingSetRepositoryTest {

    @Test
    void saveShouldWriteHashWithSnapshotVersion() {
        RedisSessionWorkingSetRepository repository = new RedisSessionWorkingSetRepository();
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = Mockito.mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);

        setField(repository, "stringRedisTemplate", redisTemplate);
        setField(repository, "keyBuilder", new SessionRedisKeyBuilder());
        setField(repository, "sessionWorkingSetRedisMapper", new SessionWorkingSetRedisMapper());
        setField(repository, "sessionWorkingSetTtlSeconds", 60L);

        SessionWorkingSetSnapshot snapshot = SessionWorkingSetSnapshot.builder()
            .sessionId("sess-1")
            .conversationId("conv-1")
            .messages(java.util.List.of(UserMessage.create("msg-1", "conv-1", "sess-1", "turn-1", "run-1", 1L, "hello")))
            .updatedAt(Instant.now())
            .build();

        repository.save(snapshot);

        ArgumentCaptor<Map<String, String>> hashCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOps).putAll(eq("agent:session:working-set:sess-1"), hashCaptor.capture());
        verify(redisTemplate).expire(eq("agent:session:working-set:sess-1"), eq(Duration.ofSeconds(60)));
        assertTrue(hashCaptor.getValue().containsKey("snapshotVersion"));
        assertTrue("1".equals(hashCaptor.getValue().get("snapshotVersion")));
    }

    @Test
    void findShouldEvictWhenSnapshotVersionInvalid() {
        RedisSessionWorkingSetRepository repository = new RedisSessionWorkingSetRepository();
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = Mockito.mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);

        setField(repository, "stringRedisTemplate", redisTemplate);
        setField(repository, "keyBuilder", new SessionRedisKeyBuilder());
        setField(repository, "sessionWorkingSetRedisMapper", new SessionWorkingSetRedisMapper());

        Map<Object, Object> hash = new HashMap<>();
        hash.put("sessionId", "sess-1");
        hash.put("conversationId", "conv-1");
        hash.put("snapshotVersion", "2");
        hash.put("messagesJson", "[]");
        when(hashOps.entries("agent:session:working-set:sess-1")).thenReturn(hash);

        var result = repository.findBySessionId("sess-1");

        assertNull(result);
        verify(redisTemplate).delete("agent:session:working-set:sess-1");
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


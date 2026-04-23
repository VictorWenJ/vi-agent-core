package com.vi.agent.core.infra.persistence.cache.session.repository;

import com.vi.agent.core.infra.persistence.cache.session.key.SessionRedisKeyBuilder;
import com.vi.agent.core.infra.persistence.cache.session.mapper.SessionContextRedisMapper;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.session.SessionStateSnapshot;
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

class RedisSessionContextRepositoryTest {

    @Test
    void saveShouldWriteHashWithSnapshotVersion() {
        RedisSessionContextRepository repository = new RedisSessionContextRepository();
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = Mockito.mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);

        setField(repository, "stringRedisTemplate", redisTemplate);
        setField(repository, "keyBuilder", new SessionRedisKeyBuilder());
        setField(repository, "sessionContextRedisMapper", new SessionContextRedisMapper());
        setField(repository, "sessionContextTtlSeconds", 60L);

        SessionStateSnapshot snapshot = SessionStateSnapshot.builder()
            .sessionId("sess-1")
            .conversationId("conv-1")
            .messages(java.util.List.of(UserMessage.create("msg-1", "conv-1", "sess-1", "turn-1", "run-1", 1L, "hello")))
            .updatedAt(Instant.now())
            .build();

        repository.save(snapshot);

        ArgumentCaptor<Map<String, String>> hashCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOps).putAll(eq("agent:session:context:sess-1"), hashCaptor.capture());
        verify(redisTemplate).expire(eq("agent:session:context:sess-1"), eq(Duration.ofSeconds(60)));
        assertTrue(hashCaptor.getValue().containsKey("snapshotVersion"));
        assertTrue("1".equals(hashCaptor.getValue().get("snapshotVersion")));
    }

    @Test
    void findShouldEvictWhenSnapshotVersionInvalid() {
        RedisSessionContextRepository repository = new RedisSessionContextRepository();
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = Mockito.mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);

        setField(repository, "stringRedisTemplate", redisTemplate);
        setField(repository, "keyBuilder", new SessionRedisKeyBuilder());
        setField(repository, "sessionContextRedisMapper", new SessionContextRedisMapper());

        Map<Object, Object> hash = new HashMap<>();
        hash.put("sessionId", "sess-1");
        hash.put("conversationId", "conv-1");
        hash.put("snapshotVersion", "2");
        hash.put("messagesJson", "[]");
        when(hashOps.entries("agent:session:context:sess-1")).thenReturn(hash);

        var result = repository.findBySessionId("sess-1");

        assertNull(result);
        verify(redisTemplate).delete("agent:session:context:sess-1");
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

package com.vi.agent.core.infra.persistence;

import com.vi.agent.core.infra.persistence.config.RedisTranscriptProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisTranscriptRepositoryTest {

    @SuppressWarnings("unchecked")
    @Test
    void saveShouldWriteRedisHash() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        RedisTranscriptProperties properties = new RedisTranscriptProperties();
        properties.setKeyPrefix("transcript:");

        RedisTranscriptRepository repository = new RedisTranscriptRepository(redisTemplate, properties);
        ConversationTranscriptEntity entity = ConversationTranscriptEntity.builder()
            .sessionId("s-1")
            .conversationId("conv-1")
            .traceId("trace-1")
            .runId("run-1")
            .messagesJson("[]")
            .toolCallsJson("[]")
            .toolResultsJson("[]")
            .updatedAt(Instant.parse("2026-04-16T00:00:00Z"))
            .build();

        repository.save(entity);

        ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(org.mockito.Mockito.eq("transcript:s-1"), mapCaptor.capture());
        Map<String, String> savedMap = mapCaptor.getValue();
        Assertions.assertEquals("conv-1", savedMap.get("conversationId"));
        Assertions.assertEquals("[]", savedMap.get("messages"));
        Assertions.assertEquals("[]", savedMap.get("toolCalls"));
        Assertions.assertEquals("[]", savedMap.get("toolResults"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void loadShouldReadRedisHash() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        when(hashOperations.entries("transcript:s-2")).thenReturn(Map.of(
            "conversationId", "conv-2",
            "traceId", "trace-2",
            "runId", "run-2",
            "messages", "[]",
            "toolCalls", "[]",
            "toolResults", "[]",
            "updatedAt", "2026-04-16T00:00:00Z"
        ));

        RedisTranscriptProperties properties = new RedisTranscriptProperties();
        properties.setKeyPrefix("transcript:");

        RedisTranscriptRepository repository = new RedisTranscriptRepository(redisTemplate, properties);
        Optional<ConversationTranscriptEntity> loaded = repository.findBySessionId("s-2");

        Assertions.assertTrue(loaded.isPresent());
        Assertions.assertEquals("conv-2", loaded.get().getConversationId());
        Assertions.assertEquals("trace-2", loaded.get().getTraceId());
    }
}

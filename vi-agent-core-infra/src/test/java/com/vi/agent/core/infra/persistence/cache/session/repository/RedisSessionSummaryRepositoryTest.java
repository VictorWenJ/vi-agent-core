package com.vi.agent.core.infra.persistence.cache.session.repository;

import com.vi.agent.core.infra.persistence.cache.session.key.SessionRedisKeyBuilder;
import com.vi.agent.core.infra.persistence.cache.session.mapper.SessionSummaryRedisMapper;
import com.vi.agent.core.model.memory.ConversationSummary;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisSessionSummaryRepositoryTest {

    @Test
    void saveShouldWriteSummarySnapshotHash() {
        RedisSessionSummaryRepository repository = buildRepository(180L);
        StringRedisTemplate redisTemplate = getField(repository, "stringRedisTemplate");
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = (HashOperations<String, Object, Object>) redisTemplate.opsForHash();

        ConversationSummary summary = summary();

        repository.save(summary);

        ArgumentCaptor<Map<String, String>> hashCaptor = ArgumentCaptor.forClass(Map.class);
        verify(hashOps).putAll(eq("agent:session:summary:sess-1"), hashCaptor.capture());
        verify(redisTemplate).expire(eq("agent:session:summary:sess-1"), eq(Duration.ofSeconds(180)));
        Map<String, String> saved = hashCaptor.getValue();
        assertEquals("sum-1", saved.get("summaryId"));
        assertEquals("sess-1", saved.get("sessionId"));
        assertEquals("2", saved.get("summaryVersion"));
        assertEquals("10", saved.get("coveredToSequenceNo"));
        assertEquals("1", saved.get("snapshotVersion"));
    }

    @Test
    void findShouldRestoreSummaryAndEvictOnlyOnCorruption() {
        RedisSessionSummaryRepository repository = buildRepository(180L);
        StringRedisTemplate redisTemplate = getField(repository, "stringRedisTemplate");
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = (HashOperations<String, Object, Object>) redisTemplate.opsForHash();

        when(hashOps.entries("agent:session:summary:sess-1")).thenReturn(validHash());

        var result = repository.findBySessionId("sess-1");

        assertTrue(result.isPresent());
        ConversationSummary summary = result.orElseThrow();
        assertNotNull(summary);
        assertEquals("sum-1", summary.getSummaryId());
        assertEquals(2L, summary.getSummaryVersion());
        assertEquals(10L, summary.getCoveredToSequenceNo());
    }

    @Test
    void findShouldEvictWhenSnapshotVersionInvalid() {
        RedisSessionSummaryRepository repository = buildRepository(180L);
        StringRedisTemplate redisTemplate = getField(repository, "stringRedisTemplate");
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = (HashOperations<String, Object, Object>) redisTemplate.opsForHash();

        Map<Object, Object> hash = validHash();
        hash.put("snapshotVersion", "2");
        when(hashOps.entries("agent:session:summary:sess-1")).thenReturn(hash);

        var result = repository.findBySessionId("sess-1");

        assertTrue(result.isEmpty());
        verify(redisTemplate).delete("agent:session:summary:sess-1");
    }

    private Map<Object, Object> validHash() {
        Map<Object, Object> hash = new HashMap<>();
        hash.put("summaryId", "sum-1");
        hash.put("sessionId", "sess-1");
        hash.put("summaryVersion", "2");
        hash.put("coveredFromSequenceNo", "1");
        hash.put("coveredToSequenceNo", "10");
        hash.put("summaryText", "历史摘要");
        hash.put("summaryTemplateKey", "summary_extract");
        hash.put("summaryTemplateVersion", "v1");
        hash.put("generatorProvider", "deepseek");
        hash.put("generatorModel", "deepseek-chat");
        hash.put("snapshotVersion", "1");
        hash.put("createdAtEpochMs", "1777075200000");
        return hash;
    }

    private ConversationSummary summary() {
        return ConversationSummary.builder()
            .summaryId("sum-1")
            .sessionId("sess-1")
            .summaryVersion(2L)
            .coveredFromSequenceNo(1L)
            .coveredToSequenceNo(10L)
            .summaryText("历史摘要")
            .summaryTemplateKey("summary_extract")
            .summaryTemplateVersion("v1")
            .generatorProvider("deepseek")
            .generatorModel("deepseek-chat")
            .createdAt(Instant.parse("2026-04-25T00:00:00Z"))
            .build();
    }

    private RedisSessionSummaryRepository buildRepository(long ttlSeconds) {
        RedisSessionSummaryRepository repository = new RedisSessionSummaryRepository();
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOps = Mockito.mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);

        setField(repository, "stringRedisTemplate", redisTemplate);
        setField(repository, "keyBuilder", new SessionRedisKeyBuilder());
        setField(repository, "sessionSummaryRedisMapper", new SessionSummaryRedisMapper());
        setField(repository, "sessionSummaryTtlSeconds", ttlSeconds);
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

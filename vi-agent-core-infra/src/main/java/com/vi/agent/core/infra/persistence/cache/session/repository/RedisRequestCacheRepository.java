package com.vi.agent.core.infra.persistence.cache.session.repository;

import com.vi.agent.core.infra.persistence.cache.session.document.RequestCacheDocument;
import com.vi.agent.core.infra.persistence.cache.session.key.SessionRedisKeyBuilder;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * requestId 辅助缓存仓储（hash 结构）。
 */
@Repository
public class RedisRequestCacheRepository {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SessionRedisKeyBuilder keyBuilder;

    @Value("${vi.agent.redis.ttl.request-cache-seconds:3600}")
    private long requestCacheTtlSeconds;

    public Optional<RequestCacheDocument> findByRequestId(String requestId) {
        if (StringUtils.isBlank(requestId)) {
            return Optional.empty();
        }
        Map<Object, Object> hash = stringRedisTemplate.opsForHash().entries(keyBuilder.requestCacheKey(requestId));
        if (MapUtils.isEmpty(hash)) {
            return Optional.empty();
        }
        return Optional.of(RequestCacheDocument.builder()
            .requestId(getString(hash, "requestId"))
            .conversationId(getString(hash, "conversationId"))
            .sessionId(getString(hash, "sessionId"))
            .turnId(getString(hash, "turnId"))
            .runId(getString(hash, "runId"))
            .runStatus(getString(hash, "runStatus"))
            .createdAtEpochMs(getLong(hash, "createdAtEpochMs"))
            .build());
    }

    public void save(RequestCacheDocument document) {
        if (document == null || StringUtils.isBlank(document.getRequestId())) {
            return;
        }
        String key = keyBuilder.requestCacheKey(document.getRequestId());
        Map<String, String> hash = new HashMap<>();
        put(hash, "requestId", document.getRequestId());
        put(hash, "conversationId", document.getConversationId());
        put(hash, "sessionId", document.getSessionId());
        put(hash, "turnId", document.getTurnId());
        put(hash, "runId", document.getRunId());
        put(hash, "runStatus", document.getRunStatus());
        put(hash, "createdAtEpochMs", document.getCreatedAtEpochMs());
        stringRedisTemplate.opsForHash().putAll(key, hash);
        stringRedisTemplate.expire(key, Duration.ofSeconds(requestCacheTtlSeconds));
    }

    public void evict(String requestId) {
        if (StringUtils.isBlank(requestId)) {
            return;
        }
        stringRedisTemplate.delete(keyBuilder.requestCacheKey(requestId));
    }

    private void put(Map<String, String> hash, String field, Object value) {
        if (value != null) {
            hash.put(field, String.valueOf(value));
        }
    }

    private String getString(Map<Object, Object> hash, String field) {
        Object value = hash.get(field);
        return value == null ? null : String.valueOf(value);
    }

    private Long getLong(Map<Object, Object> hash, String field) {
        String value = getString(hash, field);
        return StringUtils.isBlank(value) ? null : Long.parseLong(value);
    }
}

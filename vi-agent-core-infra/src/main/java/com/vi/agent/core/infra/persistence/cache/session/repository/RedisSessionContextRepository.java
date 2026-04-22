package com.vi.agent.core.infra.persistence.cache.session.repository;

import com.vi.agent.core.infra.persistence.cache.session.document.SessionContextSnapshotDocument;
import com.vi.agent.core.infra.persistence.cache.session.key.SessionRedisKeyBuilder;
import com.vi.agent.core.infra.persistence.cache.session.mapper.SessionContextRedisMapper;
import com.vi.agent.core.model.port.SessionStateRepository;
import com.vi.agent.core.model.session.SessionStateSnapshot;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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
 * Redis 会话上下文快照仓储（hash 结构）。
 */
@Slf4j
@Repository
public class RedisSessionContextRepository implements SessionStateRepository {

    private static final int SNAPSHOT_VERSION = 1;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SessionRedisKeyBuilder keyBuilder;

    @Resource
    private SessionContextRedisMapper mapper;

    @Value("${vi.agent.redis.ttl.session-context-seconds:1800}")
    private long sessionContextTtlSeconds;

    @Override
    public Optional<SessionStateSnapshot> findBySessionId(String sessionId) {
        String key = keyBuilder.sessionContextKey(sessionId);
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        if (MapUtils.isEmpty(entries)) {
            return Optional.empty();
        }

        try {
            SessionContextSnapshotDocument document = fromHash(entries);
            if (document.getSnapshotVersion() == null || document.getSnapshotVersion() != SNAPSHOT_VERSION) {
                log.warn("Redis session context snapshotVersion invalid, sessionId={}, version={}", sessionId, document.getSnapshotVersion());
                evict(sessionId);
                return Optional.empty();
            }
            SessionStateSnapshot snapshot = mapper.toModel(document);
            return Optional.ofNullable(snapshot);
        } catch (Exception ex) {
            log.warn("Redis session context parse failed, sessionId={}", sessionId, ex);
            evict(sessionId);
            return Optional.empty();
        }
    }

    @Override
    public void save(SessionStateSnapshot snapshot) {
        if (snapshot == null || StringUtils.isBlank(snapshot.getSessionId())) {
            return;
        }
        SessionContextSnapshotDocument document = mapper.toDocument(snapshot);
        document.setSnapshotVersion(SNAPSHOT_VERSION);

        String key = keyBuilder.sessionContextKey(snapshot.getSessionId());
        Map<String, String> hash = toHash(document);
        stringRedisTemplate.opsForHash().putAll(key, hash);
        stringRedisTemplate.expire(key, Duration.ofSeconds(sessionContextTtlSeconds));
    }

    @Override
    public void evict(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        stringRedisTemplate.delete(keyBuilder.sessionContextKey(sessionId));
    }

    private SessionContextSnapshotDocument fromHash(Map<Object, Object> hash) {
        SessionContextSnapshotDocument.SessionContextSnapshotDocumentBuilder builder = SessionContextSnapshotDocument.builder();
        builder.sessionId(getString(hash, "sessionId"));
        builder.conversationId(getString(hash, "conversationId"));
        builder.fromSequenceNo(getLong(hash, "fromSequenceNo"));
        builder.toSequenceNo(getLong(hash, "toSequenceNo"));
        builder.messageCount(getInteger(hash, "messageCount"));
        builder.snapshotVersion(getInteger(hash, "snapshotVersion"));
        builder.messagesJson(getString(hash, "messagesJson"));
        builder.updatedAtEpochMs(getLong(hash, "updatedAtEpochMs"));
        return builder.build();
    }

    private Map<String, String> toHash(SessionContextSnapshotDocument document) {
        Map<String, String> hash = new HashMap<>();
        put(hash, "sessionId", document.getSessionId());
        put(hash, "conversationId", document.getConversationId());
        put(hash, "fromSequenceNo", document.getFromSequenceNo());
        put(hash, "toSequenceNo", document.getToSequenceNo());
        put(hash, "messageCount", document.getMessageCount());
        put(hash, "snapshotVersion", document.getSnapshotVersion());
        put(hash, "messagesJson", document.getMessagesJson());
        put(hash, "updatedAtEpochMs", document.getUpdatedAtEpochMs());
        return hash;
    }

    private void put(Map<String, String> hash, String field, Object value) {
        if (value == null) {
            return;
        }
        hash.put(field, String.valueOf(value));
    }

    private String getString(Map<Object, Object> hash, String field) {
        Object value = hash.get(field);
        return value == null ? null : String.valueOf(value);
    }

    private Long getLong(Map<Object, Object> hash, String field) {
        String value = getString(hash, field);
        return StringUtils.isBlank(value) ? null : Long.parseLong(value);
    }

    private Integer getInteger(Map<Object, Object> hash, String field) {
        String value = getString(hash, field);
        return StringUtils.isBlank(value) ? null : Integer.parseInt(value);
    }
}

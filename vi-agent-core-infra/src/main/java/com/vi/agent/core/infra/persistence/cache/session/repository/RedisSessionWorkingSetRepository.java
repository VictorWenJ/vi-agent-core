package com.vi.agent.core.infra.persistence.cache.session.repository;

import com.vi.agent.core.infra.persistence.cache.session.document.SessionWorkingSetSnapshotDocument;
import com.vi.agent.core.infra.persistence.cache.session.key.SessionRedisKeyBuilder;
import com.vi.agent.core.infra.persistence.cache.session.mapper.SessionWorkingSetRedisMapper;
import com.vi.agent.core.model.port.SessionWorkingSetRepository;
import com.vi.agent.core.model.memory.SessionWorkingSetSnapshot;
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
/**
 * Redis 浼氳瘽涓婁笅鏂囧揩鐓т粨鍌紙hash 缁撴瀯锛夈€? */
@Slf4j
@Repository
public class RedisSessionWorkingSetRepository implements SessionWorkingSetRepository {

    private static final int SNAPSHOT_VERSION = 1;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SessionRedisKeyBuilder keyBuilder;

    @Resource
    private SessionWorkingSetRedisMapper sessionWorkingSetRedisMapper;

    @Value("${vi.agent.redis.ttl.session-working-set-seconds:1800}")
    private long sessionWorkingSetTtlSeconds;

    @Override
    public SessionWorkingSetSnapshot findBySessionId(String sessionId) {
        String key = keyBuilder.sessionWorkingSetKey(sessionId);
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        if (MapUtils.isEmpty(entries)) {
            return null;
        }

        try {
            SessionWorkingSetSnapshotDocument document = fromHash(entries);
            if (document.getSnapshotVersion() == null || document.getSnapshotVersion() != SNAPSHOT_VERSION) {
                log.warn("Redis session working set snapshotVersion invalid, sessionId={}, version={}", sessionId, document.getSnapshotVersion());
                evict(sessionId);
                return null;
            }
            return sessionWorkingSetRedisMapper.toModel(document);
        } catch (Exception ex) {
            log.warn("Redis session working set parse failed, sessionId={}", sessionId, ex);
            evict(sessionId);
            return null;
        }
    }

    @Override
    public void save(SessionWorkingSetSnapshot snapshot) {
        if (snapshot == null || StringUtils.isBlank(snapshot.getSessionId())) {
            return;
        }
        SessionWorkingSetSnapshotDocument document = sessionWorkingSetRedisMapper.toDocument(snapshot);
        document.setSnapshotVersion(SNAPSHOT_VERSION);

        String key = keyBuilder.sessionWorkingSetKey(snapshot.getSessionId());
        Map<String, String> hash = toHash(document);
        stringRedisTemplate.opsForHash().putAll(key, hash);
        stringRedisTemplate.expire(key, Duration.ofSeconds(sessionWorkingSetTtlSeconds));
    }

    @Override
    public void evict(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        stringRedisTemplate.delete(keyBuilder.sessionWorkingSetKey(sessionId));
    }

    private SessionWorkingSetSnapshotDocument fromHash(Map<Object, Object> hash) {
        return SessionWorkingSetSnapshotDocument.builder()
            .sessionId(getString(hash, "sessionId"))
            .conversationId(getString(hash, "conversationId"))
            .workingSetVersion(getLong(hash, "workingSetVersion"))
            .maxCompletedTurns(getInteger(hash, "maxCompletedTurns"))
            .summaryCoveredToSequenceNo(getLong(hash, "summaryCoveredToSequenceNo"))
            .rawMessageIdsJson(getString(hash, "rawMessageIdsJson"))
            .snapshotVersion(getInteger(hash, "snapshotVersion"))
            .messagesJson(getString(hash, "messagesJson"))
            .updatedAtEpochMs(getLong(hash, "updatedAtEpochMs"))
            .build();
    }

    private Map<String, String> toHash(SessionWorkingSetSnapshotDocument document) {
        Map<String, String> hash = new HashMap<>();
        put(hash, "sessionId", document.getSessionId());
        put(hash, "conversationId", document.getConversationId());
        put(hash, "workingSetVersion", document.getWorkingSetVersion());
        put(hash, "maxCompletedTurns", document.getMaxCompletedTurns());
        put(hash, "summaryCoveredToSequenceNo", document.getSummaryCoveredToSequenceNo());
        put(hash, "rawMessageIdsJson", document.getRawMessageIdsJson());
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


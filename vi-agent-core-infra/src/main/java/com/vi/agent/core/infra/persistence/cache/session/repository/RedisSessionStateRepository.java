package com.vi.agent.core.infra.persistence.cache.session.repository;

import com.vi.agent.core.infra.persistence.cache.session.document.SessionStateSnapshotDocument;
import com.vi.agent.core.infra.persistence.cache.session.key.SessionRedisKeyBuilder;
import com.vi.agent.core.infra.persistence.cache.session.mapper.SessionStateRedisMapper;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.port.SessionStateCacheRepository;
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
 * Redis Session State snapshot cache 仓储。
 */
@Slf4j
@Repository
public class RedisSessionStateRepository implements SessionStateCacheRepository {

    private static final int SNAPSHOT_VERSION = 1;

    /** Redis 字符串模板。 */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** Session Redis key 构建器。 */
    @Resource
    private SessionRedisKeyBuilder keyBuilder;

    /** Session State Redis mapper。 */
    @Resource
    private SessionStateRedisMapper sessionStateRedisMapper;

    /** Session State snapshot cache TTL 秒数。 */
    @Value("${vi.agent.redis.ttl.session-state-seconds:1800}")
    private long sessionStateTtlSeconds;

    /**
     * 按 session ID 读取 Redis snapshot cache。
     *
     * @param sessionId session ID
     * @return session state 快照；cache 缺失或损坏时返回 null
     */
    public Optional<SessionStateSnapshot> findBySessionId(String sessionId) {
        String key = keyBuilder.sessionStateKey(sessionId);
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        if (MapUtils.isEmpty(entries)) {
            return Optional.empty();
        }

        try {
            SessionStateSnapshotDocument document = fromHash(entries);
            if (document.getSnapshotVersion() == null || document.getSnapshotVersion() != SNAPSHOT_VERSION) {
                log.warn("Redis session state snapshotVersion invalid, sessionId={}, version={}", sessionId, document.getSnapshotVersion());
                evict(sessionId);
                return Optional.empty();
            }
            if (hasMissingRequiredField(document)) {
                log.warn("Redis session state required field missing, sessionId={}", sessionId);
                evict(sessionId);
                return Optional.empty();
            }
            return Optional.ofNullable(sessionStateRedisMapper.toModel(document));
        } catch (Exception ex) {
            log.warn("Redis session state parse failed, sessionId={}", sessionId, ex);
            evict(sessionId);
            return Optional.empty();
        }
    }

    /**
     * 写入 Redis snapshot cache。
     *
     * @param snapshot session state 快照
     */
    public void save(SessionStateSnapshot snapshot) {
        if (snapshot == null || StringUtils.isBlank(snapshot.getSessionId())) {
            return;
        }
        SessionStateSnapshotDocument document = sessionStateRedisMapper.toDocument(snapshot);
        document.setSnapshotVersion(SNAPSHOT_VERSION);

        String key = keyBuilder.sessionStateKey(snapshot.getSessionId());
        stringRedisTemplate.opsForHash().putAll(key, toHash(document));
        stringRedisTemplate.expire(key, Duration.ofSeconds(sessionStateTtlSeconds));
    }

    /**
     * 删除 Redis snapshot cache。
     *
     * @param sessionId session ID
     */
    public void evict(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        stringRedisTemplate.delete(keyBuilder.sessionStateKey(sessionId));
    }

    /** 将 Redis hash 还原为文档对象。 */
    private SessionStateSnapshotDocument fromHash(Map<Object, Object> hash) {
        return SessionStateSnapshotDocument.builder()
            .snapshotId(getString(hash, "snapshotId"))
            .sessionId(getString(hash, "sessionId"))
            .stateVersion(getLong(hash, "stateVersion"))
            .taskGoal(getString(hash, "taskGoal"))
            .stateJson(getString(hash, "stateJson"))
            .snapshotVersion(getInteger(hash, "snapshotVersion"))
            .updatedAtEpochMs(getLong(hash, "updatedAtEpochMs"))
            .build();
    }

    /** 将文档对象转换为 Redis hash。 */
    private Map<String, String> toHash(SessionStateSnapshotDocument document) {
        Map<String, String> hash = new HashMap<>();
        put(hash, "snapshotId", document.getSnapshotId());
        put(hash, "sessionId", document.getSessionId());
        put(hash, "stateVersion", document.getStateVersion());
        put(hash, "taskGoal", document.getTaskGoal());
        put(hash, "stateJson", document.getStateJson());
        put(hash, "snapshotVersion", document.getSnapshotVersion());
        put(hash, "updatedAtEpochMs", document.getUpdatedAtEpochMs());
        return hash;
    }

    /** 判断 Redis 文档是否缺少必须字段。 */
    private boolean hasMissingRequiredField(SessionStateSnapshotDocument document) {
        return StringUtils.isBlank(document.getSnapshotId())
            || StringUtils.isBlank(document.getSessionId())
            || document.getStateVersion() == null
            || StringUtils.isBlank(document.getStateJson())
            || document.getUpdatedAtEpochMs() == null;
    }

    /** 写入非空字段。 */
    private void put(Map<String, String> hash, String field, Object value) {
        if (value == null) {
            return;
        }
        hash.put(field, String.valueOf(value));
    }

    /** 读取字符串字段。 */
    private String getString(Map<Object, Object> hash, String field) {
        Object value = hash.get(field);
        return value == null ? null : String.valueOf(value);
    }

    /** 读取 Long 字段。 */
    private Long getLong(Map<Object, Object> hash, String field) {
        String value = getString(hash, field);
        return StringUtils.isBlank(value) ? null : Long.parseLong(value);
    }

    /** 读取 Integer 字段。 */
    private Integer getInteger(Map<Object, Object> hash, String field) {
        String value = getString(hash, field);
        return StringUtils.isBlank(value) ? null : Integer.parseInt(value);
    }
}

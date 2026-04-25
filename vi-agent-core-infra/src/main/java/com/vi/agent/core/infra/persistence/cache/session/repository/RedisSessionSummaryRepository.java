package com.vi.agent.core.infra.persistence.cache.session.repository;

import com.vi.agent.core.infra.persistence.cache.session.document.SessionSummarySnapshotDocument;
import com.vi.agent.core.infra.persistence.cache.session.key.SessionRedisKeyBuilder;
import com.vi.agent.core.infra.persistence.cache.session.mapper.SessionSummaryRedisMapper;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.port.SessionSummaryCacheRepository;
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
 * Redis Session Summary snapshot cache 仓储。
 */
@Slf4j
@Repository
public class RedisSessionSummaryRepository implements SessionSummaryCacheRepository {

    private static final int SNAPSHOT_VERSION = 1;

    /** Redis 字符串模板。 */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** Session Redis key 构建器。 */
    @Resource
    private SessionRedisKeyBuilder keyBuilder;

    /** Session Summary Redis mapper。 */
    @Resource
    private SessionSummaryRedisMapper sessionSummaryRedisMapper;

    /** Session Summary snapshot cache TTL 秒数。 */
    @Value("${vi.agent.redis.ttl.session-summary-seconds:1800}")
    private long sessionSummaryTtlSeconds;

    /**
     * 按 session ID 读取 Redis snapshot cache。
     *
     * @param sessionId session ID
     * @return session summary；cache 缺失或损坏时返回 null
     */
    public Optional<ConversationSummary> findBySessionId(String sessionId) {
        String key = keyBuilder.sessionSummaryKey(sessionId);
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        if (MapUtils.isEmpty(entries)) {
            return Optional.empty();
        }

        try {
            SessionSummarySnapshotDocument document = fromHash(entries);
            if (document.getSnapshotVersion() == null || document.getSnapshotVersion() != SNAPSHOT_VERSION) {
                log.warn("Redis session summary snapshotVersion invalid, sessionId={}, version={}", sessionId, document.getSnapshotVersion());
                evict(sessionId);
                return Optional.empty();
            }
            if (hasMissingRequiredField(document)) {
                log.warn("Redis session summary required field missing, sessionId={}", sessionId);
                evict(sessionId);
                return Optional.empty();
            }
            return Optional.ofNullable(sessionSummaryRedisMapper.toModel(document));
        } catch (Exception ex) {
            log.warn("Redis session summary parse failed, sessionId={}", sessionId, ex);
            evict(sessionId);
            return Optional.empty();
        }
    }

    /**
     * 写入 Redis snapshot cache。
     *
     * @param summary session summary
     */
    public void save(ConversationSummary summary) {
        if (summary == null || StringUtils.isBlank(summary.getSessionId())) {
            return;
        }
        SessionSummarySnapshotDocument document = sessionSummaryRedisMapper.toDocument(summary);
        document.setSnapshotVersion(SNAPSHOT_VERSION);

        String key = keyBuilder.sessionSummaryKey(summary.getSessionId());
        stringRedisTemplate.opsForHash().putAll(key, toHash(document));
        stringRedisTemplate.expire(key, Duration.ofSeconds(sessionSummaryTtlSeconds));
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
        stringRedisTemplate.delete(keyBuilder.sessionSummaryKey(sessionId));
    }

    /** 将 Redis hash 还原为文档对象。 */
    private SessionSummarySnapshotDocument fromHash(Map<Object, Object> hash) {
        return SessionSummarySnapshotDocument.builder()
            .summaryId(getString(hash, "summaryId"))
            .sessionId(getString(hash, "sessionId"))
            .summaryVersion(getLong(hash, "summaryVersion"))
            .coveredFromSequenceNo(getLong(hash, "coveredFromSequenceNo"))
            .coveredToSequenceNo(getLong(hash, "coveredToSequenceNo"))
            .summaryText(getString(hash, "summaryText"))
            .summaryTemplateKey(getString(hash, "summaryTemplateKey"))
            .summaryTemplateVersion(getString(hash, "summaryTemplateVersion"))
            .generatorProvider(getString(hash, "generatorProvider"))
            .generatorModel(getString(hash, "generatorModel"))
            .snapshotVersion(getInteger(hash, "snapshotVersion"))
            .createdAtEpochMs(getLong(hash, "createdAtEpochMs"))
            .build();
    }

    /** 将文档对象转换为 Redis hash。 */
    private Map<String, String> toHash(SessionSummarySnapshotDocument document) {
        Map<String, String> hash = new HashMap<>();
        put(hash, "summaryId", document.getSummaryId());
        put(hash, "sessionId", document.getSessionId());
        put(hash, "summaryVersion", document.getSummaryVersion());
        put(hash, "coveredFromSequenceNo", document.getCoveredFromSequenceNo());
        put(hash, "coveredToSequenceNo", document.getCoveredToSequenceNo());
        put(hash, "summaryText", document.getSummaryText());
        put(hash, "summaryTemplateKey", document.getSummaryTemplateKey());
        put(hash, "summaryTemplateVersion", document.getSummaryTemplateVersion());
        put(hash, "generatorProvider", document.getGeneratorProvider());
        put(hash, "generatorModel", document.getGeneratorModel());
        put(hash, "snapshotVersion", document.getSnapshotVersion());
        put(hash, "createdAtEpochMs", document.getCreatedAtEpochMs());
        return hash;
    }

    /** 判断 Redis 文档是否缺少必须字段。 */
    private boolean hasMissingRequiredField(SessionSummarySnapshotDocument document) {
        return StringUtils.isBlank(document.getSummaryId())
            || StringUtils.isBlank(document.getSessionId())
            || document.getSummaryVersion() == null
            || document.getCoveredFromSequenceNo() == null
            || document.getCoveredToSequenceNo() == null
            || StringUtils.isBlank(document.getSummaryText())
            || StringUtils.isBlank(document.getSummaryTemplateKey())
            || StringUtils.isBlank(document.getSummaryTemplateVersion())
            || document.getCreatedAtEpochMs() == null;
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

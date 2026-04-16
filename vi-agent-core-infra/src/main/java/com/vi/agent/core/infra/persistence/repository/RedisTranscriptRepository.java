package com.vi.agent.core.infra.persistence.repository;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.common.util.ValidationUtils;
import com.vi.agent.core.infra.persistence.config.RedisTranscriptProperties;
import com.vi.agent.core.infra.persistence.entity.TranscriptEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Redis Hash Transcript 仓储实现。
 */
@Slf4j
@RequiredArgsConstructor
public class RedisTranscriptRepository implements TranscriptRepository {

    private static final String FIELD_CONVERSATION_ID = "conversationId";
    private static final String FIELD_TRACE_ID = "traceId";
    private static final String FIELD_RUN_ID = "runId";
    private static final String FIELD_MESSAGES = "messages";
    private static final String FIELD_TOOL_CALLS = "toolCalls";
    private static final String FIELD_TOOL_RESULTS = "toolResults";
    private static final String FIELD_UPDATED_AT = "updatedAt";

    /** Redis 客户端。 */
    private final StringRedisTemplate redisTemplate;

    /** Redis 配置。 */
    private final RedisTranscriptProperties properties;

    @Override
    public Optional<TranscriptEntity> findBySessionId(String sessionId) {
        ValidationUtils.requireNonBlank(sessionId, "sessionId");
        try {
            String key = redisKey(sessionId);

            Map<Object, Object> map = redisTemplate.opsForHash().entries(key);
            if (MapUtils.isEmpty(map)) {
                return Optional.empty();
            }

            TranscriptEntity entity = TranscriptEntity.builder()
                .sessionId(sessionId)
                .conversationId(asString(map.get(FIELD_CONVERSATION_ID)))
                .traceId(asString(map.get(FIELD_TRACE_ID)))
                .runId(asString(map.get(FIELD_RUN_ID)))
                .messagesJson(asString(map.get(FIELD_MESSAGES)))
                .toolCallsJson(asString(map.get(FIELD_TOOL_CALLS)))
                .toolResultsJson(asString(map.get(FIELD_TOOL_RESULTS)))
                .updatedAt(parseInstant(asString(map.get(FIELD_UPDATED_AT))))
                .build();

            log.info("RedisTranscriptRepository findBySessionId finished sessionId={} key={} entity={}", sessionId, key, JsonUtils.toJson(entity));
            return Optional.of(entity);
        } catch (Exception e) {
            throw new AgentRuntimeException(ErrorCode.TRANSCRIPT_STORE_FAILED, "Redis 读取 transcript 失败", e);
        }
    }

    @Override
    public void save(TranscriptEntity entity) {
        ValidationUtils.requireNonBlank(entity.getSessionId(), "sessionId");
        try {
            String sessionId = entity.getSessionId();

            String key = redisKey(sessionId);

            Map<String, String> hash = new HashMap<>();
            hash.put(FIELD_CONVERSATION_ID, safe(entity.getConversationId()));
            hash.put(FIELD_TRACE_ID, safe(entity.getTraceId()));
            hash.put(FIELD_RUN_ID, safe(entity.getRunId()));
            hash.put(FIELD_MESSAGES, safe(entity.getMessagesJson()));
            hash.put(FIELD_TOOL_CALLS, safe(entity.getToolCallsJson()));
            hash.put(FIELD_TOOL_RESULTS, safe(entity.getToolResultsJson()));
            hash.put(FIELD_UPDATED_AT, String.valueOf(entity.getUpdatedAt() == null ? Instant.now() : entity.getUpdatedAt()));

            redisTemplate.opsForHash().putAll(key, hash);

            log.info("RedisTranscriptRepository save finished sessionId={} key={} entity={}", sessionId, key, JsonUtils.toJson(entity));
        } catch (Exception e) {
            throw new AgentRuntimeException(ErrorCode.TRANSCRIPT_STORE_FAILED, "Redis 保存 transcript 失败", e);
        }
    }

    private String redisKey(String sessionId) {
        String prefix = properties.getKeyPrefix() == null ? "transcript:" : properties.getKeyPrefix();
        return prefix + sessionId;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        return Instant.parse(value);
    }
}

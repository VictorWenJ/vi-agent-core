package com.vi.agent.core.infra.persistence.cache.session.repository;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.infra.persistence.cache.session.document.SessionStateCacheDocument;
import com.vi.agent.core.infra.persistence.cache.session.key.SessionRedisKeyBuilder;
import com.vi.agent.core.infra.persistence.cache.session.mapper.SessionStateRedisMapper;
import com.vi.agent.core.model.port.SessionStateRepository;
import com.vi.agent.core.model.session.SessionStateSnapshot;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

/**
 * Redis session state repository.
 */
@Repository
public class RedisSessionStateRepository implements SessionStateRepository {

    @Resource
    private StringRedisTemplate redisTemplate;

    @Resource
    private SessionRedisKeyBuilder keyBuilder;

    @Resource
    private SessionStateRedisMapper mapper;

    @Override
    public Optional<SessionStateSnapshot> findBySessionId(String sessionId) {
        String value = redisTemplate.opsForValue().get(keyBuilder.sessionStateKey(sessionId));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        SessionStateCacheDocument document = JsonUtils.jsonToBean(value, SessionStateCacheDocument.class);
        return Optional.ofNullable(document).map(mapper::toModel);
    }

    @Override
    public void save(SessionStateSnapshot snapshot) {
        SessionStateCacheDocument document = mapper.toDocument(snapshot);
        redisTemplate.opsForValue().set(keyBuilder.sessionStateKey(snapshot.getSessionId()), JsonUtils.toJson(document));
    }

    @Override
    public void evict(String sessionId) {
        redisTemplate.delete(keyBuilder.sessionStateKey(sessionId));
    }
}

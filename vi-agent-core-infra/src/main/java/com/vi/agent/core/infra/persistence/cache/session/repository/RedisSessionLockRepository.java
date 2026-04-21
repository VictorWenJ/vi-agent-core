package com.vi.agent.core.infra.persistence.cache.session.repository;

import com.vi.agent.core.infra.persistence.cache.session.key.SessionRedisKeyBuilder;
import com.vi.agent.core.model.port.SessionLockRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis session lock repository.
 */
@Repository
public class RedisSessionLockRepository implements SessionLockRepository {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SessionRedisKeyBuilder keyBuilder;

    @Override
    public boolean tryLock(String sessionId, String runId, Duration ttl) {
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(keyBuilder.sessionLockKey(sessionId), runId, ttl);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void unlock(String sessionId, String runId) {
        String key = keyBuilder.sessionLockKey(sessionId);
        String current = stringRedisTemplate.opsForValue().get(key);
        if (runId.equals(current)) {
            stringRedisTemplate.delete(key);
        }
    }
}

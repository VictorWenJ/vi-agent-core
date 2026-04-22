package com.vi.agent.core.infra.persistence.cache.session.repository;

import com.vi.agent.core.infra.persistence.cache.session.key.SessionRedisKeyBuilder;
import com.vi.agent.core.model.port.SessionLockRepository;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

/**
 * Redis 会话锁仓储（hash + Lua 原子语义）。
 */
@Repository
public class RedisSessionLockRepository implements SessionLockRepository {

    private static final DefaultRedisScript<Long> TRY_LOCK_SCRIPT = new DefaultRedisScript<>(
        """
            if redis.call('EXISTS', KEYS[1]) == 0 then
                redis.call('HSET', KEYS[1], 'sessionId', ARGV[1], 'runId', ARGV[2], 'lockedAtEpochMs', ARGV[3])
                redis.call('EXPIRE', KEYS[1], ARGV[4])
                return 1
            end
            return 0
            """,
        Long.class
    );

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
        """
            local currentRunId = redis.call('HGET', KEYS[1], 'runId')
            if currentRunId and currentRunId == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """,
        Long.class
    );

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SessionRedisKeyBuilder keyBuilder;

    @Override
    public boolean tryLock(String sessionId, String runId, Duration ttl) {
        if (StringUtils.isAnyBlank(sessionId, runId) || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return false;
        }
        String key = keyBuilder.sessionLockKey(sessionId);
        long nowEpochMs = System.currentTimeMillis();
        long ttlSeconds = Math.max(1L, ttl.toSeconds());
        Long result = stringRedisTemplate.execute(
            TRY_LOCK_SCRIPT,
            List.of(key),
            sessionId,
            runId,
            String.valueOf(nowEpochMs),
            String.valueOf(ttlSeconds)
        );
        return result != null && result == 1L;
    }

    @Override
    public void unlock(String sessionId, String runId) {
        if (StringUtils.isAnyBlank(sessionId, runId)) {
            return;
        }
        stringRedisTemplate.execute(
            UNLOCK_SCRIPT,
            List.of(keyBuilder.sessionLockKey(sessionId)),
            runId
        );
    }
}

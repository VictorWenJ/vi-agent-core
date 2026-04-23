package com.vi.agent.core.infra.persistence.cache.session.repository;

import com.vi.agent.core.infra.persistence.cache.session.key.SessionRedisKeyBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisSessionLockRepositoryTest {

    @Test
    void tryLockShouldUseLuaAndReturnTrueWhenScriptReturnsOne() {
        RedisSessionLockRepository repository = new RedisSessionLockRepository();
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);

        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any())).thenReturn(1L);

        setField(repository, "stringRedisTemplate", redisTemplate);
        setField(repository, "keyBuilder", new SessionRedisKeyBuilder());

        boolean locked = repository.tryLock("sess-1", "run-1", Duration.ofSeconds(30));

        assertTrue(locked);
    }

    @Test
    void tryLockShouldReturnFalseWhenInvalidArguments() {
        RedisSessionLockRepository repository = new RedisSessionLockRepository();

        assertFalse(repository.tryLock("", "run-1", Duration.ofSeconds(30)));
        assertFalse(repository.tryLock("sess-1", "", Duration.ofSeconds(30)));
        assertFalse(repository.tryLock("sess-1", "run-1", Duration.ZERO));
    }

    @Test
    void unlockShouldInvokeLuaScript() {
        RedisSessionLockRepository repository = new RedisSessionLockRepository();
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);

        setField(repository, "stringRedisTemplate", redisTemplate);
        setField(repository, "keyBuilder", new SessionRedisKeyBuilder());

        repository.unlock("sess-1", "run-1");

        verify(redisTemplate).execute(any(DefaultRedisScript.class), anyList(), any());
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

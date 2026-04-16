package com.vi.agent.core.app.config;

import com.vi.agent.core.infra.persistence.repository.RedisTranscriptRepository;
import com.vi.agent.core.infra.persistence.mapper.RedisTranscriptMapper;
import com.vi.agent.core.infra.persistence.repository.TranscriptRepository;
import com.vi.agent.core.infra.persistence.adapter.TranscriptStoreAdapter;
import com.vi.agent.core.infra.persistence.config.RedisTranscriptProperties;
import com.vi.agent.core.runtime.port.TranscriptStore;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
public class PersistenceConfig {

    @Bean
    @ConfigurationProperties(prefix = "vi.agent.transcript.redis")
    public RedisTranscriptProperties redisTranscriptProperties() {
        return new RedisTranscriptProperties();
    }

    @Bean
    public TranscriptRepository transcriptRepository(StringRedisTemplate stringRedisTemplate, RedisTranscriptProperties redisTranscriptProperties) {
        return new RedisTranscriptRepository(stringRedisTemplate, redisTranscriptProperties);
    }

    @Bean
    public RedisTranscriptMapper transcriptRedisMapper() {
        return new RedisTranscriptMapper();
    }

    @Bean
    public TranscriptStore transcriptStore(TranscriptRepository transcriptRepository, RedisTranscriptMapper redisTranscriptMapper) {
        return new TranscriptStoreAdapter(transcriptRepository, redisTranscriptMapper);
    }
}
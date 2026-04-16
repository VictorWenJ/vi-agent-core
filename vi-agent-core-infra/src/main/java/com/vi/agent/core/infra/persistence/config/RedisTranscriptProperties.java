package com.vi.agent.core.infra.persistence.config;

import lombok.*;

import java.time.Instant;

/**
 * Redis Transcript 配置。
 */
@Getter
@Setter
public class RedisTranscriptProperties {

    /** Redis key 前缀。 */
    private String keyPrefix = "transcript:";
}

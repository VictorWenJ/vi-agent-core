package com.vi.agent.core.app.api.dto.response;

import lombok.Builder;
import lombok.Getter;

/**
 * API usage info.
 */
@Getter
@Builder
public class UsageInfo {

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer totalTokens;

    private String provider;

    private String model;
}

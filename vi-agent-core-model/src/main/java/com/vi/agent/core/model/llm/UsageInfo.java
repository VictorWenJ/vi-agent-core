package com.vi.agent.core.model.llm;

import lombok.Builder;
import lombok.Getter;

/**
 * LLM usage info.
 */
@Getter
@Builder
public class UsageInfo {

    private final Integer inputTokens;

    private final Integer outputTokens;

    private final Integer totalTokens;

    private final String provider;

    private final String model;

    public static UsageInfo empty() {
        return UsageInfo.builder()
            .inputTokens(0)
            .outputTokens(0)
            .totalTokens(0)
            .build();
    }

    public static UsageInfo sum(UsageInfo left, UsageInfo right) {
        UsageInfo l = left == null ? empty() : left;
        UsageInfo r = right == null ? empty() : right;
        return UsageInfo.builder()
            .inputTokens(nullToZero(l.getInputTokens()) + nullToZero(r.getInputTokens()))
            .outputTokens(nullToZero(l.getOutputTokens()) + nullToZero(r.getOutputTokens()))
            .totalTokens(nullToZero(l.getTotalTokens()) + nullToZero(r.getTotalTokens()))
            .provider(r.getProvider() == null ? l.getProvider() : r.getProvider())
            .model(r.getModel() == null ? l.getModel() : r.getModel())
            .build();
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}

package com.vi.agent.core.model.memory;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工具结果新鲜度策略。
 */
@Getter
@AllArgsConstructor
public enum ToolOutcomeFreshnessPolicy {

    TURN_ONLY("turn_only", "仅当前轮次有效"),

    TTL_CONFIGURED("ttl_configured", "按配置 TTL 有效"),

    SESSION("session", "会话内有效"),

    STATIC("static", "静态长期有效"),

    MANUAL_REVALIDATE("manual_revalidate", "需要手动重新验证");

    private final String value;

    private final String desc;
}

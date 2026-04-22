package com.vi.agent.core.model.session;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Session 状态枚举。
 */
@Getter
@AllArgsConstructor
public enum SessionStatus {

    /** 活跃状态。 */
    ACTIVE("active", "活跃状态"),

    /** 已归档状态。 */
    ARCHIVED("archived", "已归档状态"),

    /** 已失败状态。 */
    FAILED("failed", "已失败状态");

    private final String value;

    private final String desc;
}

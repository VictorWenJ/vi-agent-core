package com.vi.agent.core.model.session;

/**
 * Session 状态枚举。
 */
public enum SessionStatus {
    /** 活跃状态，可继续接收新 turn。 */
    ACTIVE,
    /** 已归档状态，不再接收新 turn。 */
    ARCHIVED,
    /** 因执行或持久化问题进入失败状态。 */
    FAILED
}

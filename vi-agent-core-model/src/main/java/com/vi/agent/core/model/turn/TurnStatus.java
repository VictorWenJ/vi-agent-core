package com.vi.agent.core.model.turn;

/**
 * Turn 状态枚举。
 */
public enum TurnStatus {
    /** 执行中。 */
    RUNNING,
    /** 执行成功完成。 */
    COMPLETED,
    /** 执行失败。 */
    FAILED,
    /** 执行被取消。 */
    CANCELLED
}

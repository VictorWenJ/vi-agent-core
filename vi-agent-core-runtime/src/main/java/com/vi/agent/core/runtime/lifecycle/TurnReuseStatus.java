package com.vi.agent.core.runtime.lifecycle;

/**
 * 基于 requestId 检查既有 turn 时的复用决策状态。
 */
public enum TurnReuseStatus {
    /** 新建 turn，继续执行主链路。 */
    CREATED,
    /** 命中已完成 turn，直接复用返回。 */
    COMPLETED,
    /** 命中运行中 turn，返回处理中状态。 */
    RUNNING,
    /** 命中失败 turn，进入失败处理策略。 */
    FAILED
}

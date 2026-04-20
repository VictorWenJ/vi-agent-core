package com.vi.agent.core.model.runtime;

/**
 * 对外协议暴露的 run 状态枚举。
 */
public enum RunStatus {
    /** 运行中。 */
    RUNNING,
    /** 运行成功完成。 */
    COMPLETED,
    /** 运行失败结束。 */
    FAILED,
    /** 运行被取消。 */
    CANCELLED
}

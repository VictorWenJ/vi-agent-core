package com.vi.agent.core.model.message;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息状态枚举。
 */
@Getter
@AllArgsConstructor
public enum MessageStatus {

    /** 已完成。 */
    COMPLETED("completed", "已完成"),

    /** 运行中。 */
    RUNNING("running", "运行中"),

    /** 已失败。 */
    FAILED("failed", "已失败"),

    /** 已取消。 */
    CANCELLED("cancelled", "已取消");

    private final String value;

    private final String desc;
}

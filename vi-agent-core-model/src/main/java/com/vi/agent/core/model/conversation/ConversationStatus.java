package com.vi.agent.core.model.conversation;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 会话窗口状态枚举。
 */
@Getter
@AllArgsConstructor
public enum ConversationStatus {

    /** 活跃状态。 */
    ACTIVE("active", "活跃状态"),

    /** 关闭状态。 */
    CLOSED("closed", "关闭状态"),

    /** 删除状态。 */
    DELETED("deleted", "删除状态");

    private final String value;

    private final String desc;
}

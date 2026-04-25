package com.vi.agent.core.model.memory;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 未闭环事项类型。
 */
@Getter
@AllArgsConstructor
public enum OpenLoopKind {

    USER_CONFIRMATION_REQUIRED("user_confirmation_required", "需要用户确认"),

    USER_INPUT_REQUIRED("user_input_required", "需要用户补充输入"),

    FOLLOW_UP_ACTION("follow_up_action", "后续动作"),

    REVIEW_PENDING("review_pending", "等待评审"),

    SYSTEM_PENDING("system_pending", "系统待处理");

    private final String value;

    private final String desc;
}

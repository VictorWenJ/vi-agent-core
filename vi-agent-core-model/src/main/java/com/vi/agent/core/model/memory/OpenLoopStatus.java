package com.vi.agent.core.model.memory;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 未闭环事项状态。
 */
@Getter
@AllArgsConstructor
public enum OpenLoopStatus {

    OPEN("open", "已打开"),

    WAITING_USER("waiting_user", "等待用户"),

    BLOCKED("blocked", "被阻塞"),

    CLOSED("closed", "已关闭");

    private final String value;

    private final String desc;
}

package com.vi.agent.core.model.memory;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户偏好的细节程度。
 */
@Getter
@AllArgsConstructor
public enum DetailLevel {

    BRIEF("brief", "简略"),

    NORMAL("normal", "常规"),

    DETAILED("detailed", "详细");

    private final String value;

    private final String desc;
}

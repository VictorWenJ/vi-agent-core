package com.vi.agent.core.model.memory;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户偏好的回答风格。
 */
@Getter
@AllArgsConstructor
public enum AnswerStyle {

    DIRECT("direct", "直接回答"),

    STRUCTURED("structured", "结构化回答"),

    DECISIVE("decisive", "高决策回答");

    private final String value;

    private final String desc;
}

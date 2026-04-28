package com.vi.agent.core.model.prompt;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * prompt 输入变量类型。
 */
@Getter
@AllArgsConstructor
public enum PromptInputVariableType {

    /** 普通文本。 */
    TEXT("text", "普通文本"),

    /** 已序列化 JSON 文本。 */
    JSON("json", "已序列化 JSON 文本"),

    /** 数字文本。 */
    NUMBER("number", "数字文本"),

    /** 布尔文本。 */
    BOOLEAN("boolean", "布尔文本"),

    /** 枚举名称文本。 */
    ENUM("enum", "枚举名称文本");

    /** manifest 中使用的稳定值。 */
    private final String value;

    /** 中文说明。 */
    private final String description;
}

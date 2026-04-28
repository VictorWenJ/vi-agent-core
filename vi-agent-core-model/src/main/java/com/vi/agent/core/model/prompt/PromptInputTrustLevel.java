package com.vi.agent.core.model.prompt;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * prompt 输入变量可信级别。
 */
@Getter
@AllArgsConstructor
public enum PromptInputTrustLevel {

    /** 可信控制变量。 */
    TRUSTED_CONTROL("trusted_control", "可信控制变量"),

    /** 不可信数据变量。 */
    UNTRUSTED_DATA("untrusted_data", "不可信数据变量");

    /** manifest 中使用的稳定值。 */
    private final String value;

    /** 中文说明。 */
    private final String description;
}

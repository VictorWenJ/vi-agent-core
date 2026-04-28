package com.vi.agent.core.model.prompt;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * prompt 渲染输入变量声明。
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class PromptInputVariable {

    /** 变量名。 */
    String variableName;

    /** 变量类型。 */
    PromptInputVariableType variableType;

    /** 变量可信级别。 */
    PromptInputTrustLevel trustLevel;

    /** 变量放置位置。 */
    PromptInputPlacement placement;

    /** 是否必填。 */
    Boolean required;

    /** 最大字符数。 */
    Integer maxChars;

    /** 截断标记。 */
    String truncateMarker;

    /** 变量说明。 */
    String description;

    /** 默认值。 */
    String defaultValue;
}

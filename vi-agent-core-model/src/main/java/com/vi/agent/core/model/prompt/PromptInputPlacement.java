package com.vi.agent.core.model.prompt;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * prompt 输入变量放置位置。
 */
@Getter
@AllArgsConstructor
public enum PromptInputPlacement {

    /** 指令块。 */
    INSTRUCTION_BLOCK("instruction_block", "指令块"),

    /** 数据块。 */
    DATA_BLOCK("data_block", "数据块"),

    /** 元数据块。 */
    METADATA_BLOCK("metadata_block", "元数据块");

    /** manifest 中使用的稳定值。 */
    private final String value;

    /** 中文说明。 */
    private final String description;
}

package com.vi.agent.core.model.context;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Context block 装配决策。
 */
@Getter
@AllArgsConstructor
public enum ContextAssemblyDecision {

    KEEP("keep", "保留"),

    TRIM("trim", "裁剪"),

    DROP("drop", "丢弃"),

    REPLACE_WITH_REFERENCE("replace_with_reference", "替换为引用");

    private final String value;

    private final String desc;
}

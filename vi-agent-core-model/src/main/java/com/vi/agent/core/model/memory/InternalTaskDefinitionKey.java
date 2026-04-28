package com.vi.agent.core.model.memory;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 非 LLM 内部任务定义 key。
 */
@Getter
@AllArgsConstructor
public enum InternalTaskDefinitionKey {

    /** 确定性证据绑定任务。 */
    EVIDENCE_BIND_DETERMINISTIC("evidence_bind_deterministic", "确定性证据绑定任务");

    /** 审计中使用的稳定值。 */
    private final String value;

    /** 中文说明。 */
    private final String description;
}

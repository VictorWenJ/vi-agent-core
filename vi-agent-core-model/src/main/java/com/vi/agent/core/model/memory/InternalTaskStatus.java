package com.vi.agent.core.model.memory;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内部 LLM 任务状态。
 */
@Getter
@AllArgsConstructor
public enum InternalTaskStatus {

    PENDING("pending", "等待执行"),

    RUNNING("running", "执行中"),

    SUCCEEDED("succeeded", "执行成功"),

    FAILED("failed", "执行失败"),

    DEGRADED("degraded", "降级完成"),

    SKIPPED("skipped", "策略跳过");

    private final String value;

    private final String desc;
}

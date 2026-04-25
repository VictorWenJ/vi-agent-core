package com.vi.agent.core.model.memory;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内部 LLM 任务类型。
 */
@Getter
@AllArgsConstructor
public enum InternalTaskType {

    STATE_EXTRACTION("state_extraction", "状态抽取"),

    SUMMARY_UPDATE("summary_update", "摘要更新"),

    STATE_EXTRACT("state_extract", "状态抽取"),

    SUMMARY_EXTRACT("summary_extract", "摘要抽取"),

    STATE_REPAIR("state_repair", "状态修复"),

    SUMMARY_REPAIR("summary_repair", "摘要修复"),

    EVIDENCE_ENRICH("evidence_enrich", "证据增强");

    private final String value;

    private final String desc;
}

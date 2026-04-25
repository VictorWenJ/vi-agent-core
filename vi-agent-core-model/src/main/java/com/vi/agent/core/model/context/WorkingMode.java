package com.vi.agent.core.model.context;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 当前任务工作模式。
 */
@Getter
@AllArgsConstructor
public enum WorkingMode {

    GENERAL_CONVERSATION("general_conversation", "普通对话"),

    ARCHITECTURE_DESIGN("architecture_design", "架构设计"),

    DOCUMENT_GOVERNANCE("document_governance", "文档治理"),

    CODE_REVIEW("code_review", "代码评审"),

    TASK_EXECUTION("task_execution", "任务执行"),

    TOOL_FOLLOW_UP("tool_follow_up", "工具结果跟进"),

    DEBUG_ANALYSIS("debug_analysis", "调试分析");

    private final String value;

    private final String desc;
}

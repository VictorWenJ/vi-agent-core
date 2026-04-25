package com.vi.agent.core.model.context;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 上下文使用视图类型。
 */
@Getter
@AllArgsConstructor
public enum ContextViewType {

    MAIN_AGENT("main_agent", "主代理视图"),

    INTERNAL_STATE_TASK("internal_state_task", "内部状态抽取任务视图"),

    INTERNAL_SUMMARY_TASK("internal_summary_task", "内部摘要抽取任务视图"),

    SUBAGENT_RESERVED("subagent_reserved", "子代理预留视图");

    private final String value;

    private final String desc;
}

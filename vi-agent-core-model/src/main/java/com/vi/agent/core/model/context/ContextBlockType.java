package com.vi.agent.core.model.context;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * WorkingContext block 类型。
 */
@Getter
@AllArgsConstructor
public enum ContextBlockType {

    RUNTIME_INSTRUCTION("runtime_instruction", "运行时指令块"),

    SESSION_STATE("session_state", "会话状态块"),

    CONVERSATION_SUMMARY("conversation_summary", "会话摘要块"),

    RECENT_MESSAGES("recent_messages", "最近消息块"),

    CURRENT_USER_MESSAGE("current_user_message", "当前用户消息块"),

    CONTEXT_REFERENCE("context_reference", "上下文引用块"),

    COMPACTION_NOTE("compaction_note", "压缩说明块");

    private final String value;

    private final String desc;
}

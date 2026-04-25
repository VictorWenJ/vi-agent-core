package com.vi.agent.core.model.context;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Context block 内容来源类型。
 */
@Getter
@AllArgsConstructor
public enum ContextSourceType {

    /** 运行时指令来源。 */
    RUNTIME_INSTRUCTION("runtime_instruction", "运行时指令来源"),

    /** session state 快照来源。 */
    SESSION_STATE_SNAPSHOT("session_state_snapshot", "会话状态快照来源"),

    /** 会话摘要来源。 */
    CONVERSATION_SUMMARY("conversation_summary", "会话摘要来源"),

    /** 已完成 transcript 消息来源。 */
    TRANSCRIPT_MESSAGE("transcript_message", "已完成 transcript 消息来源"),

    /** 当前用户消息来源。 */
    CURRENT_USER_MESSAGE("current_user_message", "当前用户消息来源"),

    /** 上下文引用来源。 */
    CONTEXT_REFERENCE("context_reference", "上下文引用来源"),

    /** 压缩说明来源。 */
    COMPACTION_NOTE("compaction_note", "压缩说明来源");

    private final String value;

    private final String desc;
}

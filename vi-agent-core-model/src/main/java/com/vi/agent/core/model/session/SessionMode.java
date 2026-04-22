package com.vi.agent.core.model.session;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Session 解析模式枚举。
 */
@Getter
@AllArgsConstructor
public enum SessionMode {

    /** 新建会话窗口与首个会话段。 */
    NEW_CONVERSATION("new_conversation", "新建会话窗口与首个会话段"),

    /** 延续当前活跃会话段。 */
    CONTINUE_ACTIVE_SESSION("continue_active_session", "延续当前活跃会话段"),

    /** 精确延续指定会话段。 */
    CONTINUE_EXACT_SESSION("continue_exact_session", "精确延续指定会话段"),

    /** 在同一会话窗口下开启新的会话段。 */
    START_NEW_SESSION("start_new_session", "在同一会话窗口下开启新的会话段");

    private final String value;

    private final String desc;
}

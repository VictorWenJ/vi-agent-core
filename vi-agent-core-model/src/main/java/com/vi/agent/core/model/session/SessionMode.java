package com.vi.agent.core.model.session;

/**
 * Session 模式枚举。
 */
public enum SessionMode {
    /** 创建全新 conversation 及首个 session。 */
    NEW_CONVERSATION,
    /** 延续 conversation 当前活跃 session。 */
    CONTINUE_ACTIVE_SESSION,
    /** 精确延续指定 session。 */
    CONTINUE_EXACT_SESSION,
    /** 归档当前活跃 session 并新建 session。 */
    START_NEW_SESSION
}

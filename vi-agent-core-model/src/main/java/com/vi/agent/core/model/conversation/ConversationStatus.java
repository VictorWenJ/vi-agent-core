package com.vi.agent.core.model.conversation;

/**
 * Conversation 状态枚举。
 */
public enum ConversationStatus {
    /** 活跃状态，可继续接收新的 session 或 turn。 */
    ACTIVE,
    /** 已关闭状态，不再接收新的交互。 */
    CLOSED,
    /** 逻辑删除状态。 */
    DELETED
}

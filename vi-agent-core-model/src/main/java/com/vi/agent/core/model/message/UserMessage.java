package com.vi.agent.core.model.message;

/**
 * 用户消息。
 */
public class UserMessage extends BaseMessage {

    public UserMessage(String content) {
        super("user", content);
    }
}

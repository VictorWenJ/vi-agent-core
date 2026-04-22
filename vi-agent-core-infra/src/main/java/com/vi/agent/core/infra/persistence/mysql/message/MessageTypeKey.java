package com.vi.agent.core.infra.persistence.mysql.message;

import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.MessageType;

/**
 * 消息处理器路由键。
 */
public record MessageTypeKey(MessageRole role, MessageType messageType) {
}

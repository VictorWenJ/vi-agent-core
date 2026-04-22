package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vi.agent.core.model.conversation.ConversationStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话窗口持久化实体。
 */
@Data
@TableName("agent_conversation")
public class AgentConversationEntity {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话窗口ID。 */
    private String conversationId;

    /** 会话窗口标题。 */
    private String title;

    /** 会话窗口状态。 */
    private ConversationStatus status;

    /** 当前活跃会话段ID。 */
    private String activeSessionId;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;

    /** 最新消息时间。 */
    private LocalDateTime lastMessageAt;
}

package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_conversation")
/**
 * conversation 聚合元数据的 MySQL 实体。
 * 该表存储 conversation 级属性以及当前活跃 session 指针。
 */
public class AgentConversationEntity {

    /** MySQL 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外暴露的全局唯一 conversation 标识。 */
    private String conversationId;

    /** 人类可读的会话标题。 */
    private String title;

    /** conversation 状态码。 */
    private String status;

    /** 当前活跃 session 标识。 */
    private String activeSessionId;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;

    /** 最近一条消息时间。 */
    private LocalDateTime lastMessageAt;
}

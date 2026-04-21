package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * conversation/session/turn 时间线消息事实的 MySQL 实体。
 */
@Data
@TableName("agent_message")
public class AgentMessageEntity {

    /**
     * MySQL 自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 全局唯一消息标识。
     */
    private String messageId;

    /**
     * 归属的 conversation 标识。
     */
    private String conversationId;

    /**
     * 归属的 session 标识。
     */
    private String sessionId;

    /**
     * 归属的 turn 标识。
     */
    private String turnId;

    /**
     * 消息角色编码，例如 USER/ASSISTANT/TOOL/SYSTEM。
     */
    private String role;

    /**
     * 运行时协议中的消息类型编码。
     */
    private String messageType;

    /**
     * 用于 session 内稳定排序的序号。
     */
    private Long sequenceNo;

    /**
     * 消息正文内容。
     */
    private String content;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
}

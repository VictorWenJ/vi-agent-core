package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vi.agent.core.model.session.SessionStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Session 持久化实体。
 */
@Data
@TableName("agent_session")
public class AgentSessionEntity {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** Session ID。 */
    private String sessionId;

    /** 所属会话窗口ID。 */
    private String conversationId;

    /** 父Session ID。 */
    private String parentSessionId;

    /** Session状态。 */
    private SessionStatus status;

    /** 归档原因。 */
    private String archiveReason;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;

    /** 归档时间。 */
    private LocalDateTime archivedAt;
}

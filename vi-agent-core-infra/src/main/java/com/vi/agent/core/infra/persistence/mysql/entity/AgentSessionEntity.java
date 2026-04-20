package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_session")
/**
 * conversation 下 session 生命周期元数据的 MySQL 实体。
 */
public class AgentSessionEntity {

    /** MySQL 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 全局唯一 session 标识。 */
    private String sessionId;

    /** 归属的 conversation 标识。 */
    private String conversationId;

    /** 由已有 session 派生时的父 session 标识。 */
    private String parentSessionId;

    /** session 状态码，例如 ACTIVE/ARCHIVED/FAILED。 */
    private String status;

    /** 归档原因。 */
    private String archiveReason;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;

    /** 归档完成时间。 */
    private LocalDateTime archivedAt;
}

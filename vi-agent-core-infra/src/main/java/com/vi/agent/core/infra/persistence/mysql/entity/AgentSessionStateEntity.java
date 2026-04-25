package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * session 结构化状态快照持久化实体。
 */
@Data
@TableName("agent_session_state")
public class AgentSessionStateEntity {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** state 快照 ID。 */
    private String snapshotId;

    /** session ID。 */
    private String sessionId;

    /** state 版本号，单 session 单调递增。 */
    private Long stateVersion;

    /** 当前任务目标冗余字段，便于查询。 */
    private String taskGoal;

    /** 完整 state JSON。 */
    private String stateJson;

    /** 本次 state 来源 run ID。 */
    private String sourceRunId;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}

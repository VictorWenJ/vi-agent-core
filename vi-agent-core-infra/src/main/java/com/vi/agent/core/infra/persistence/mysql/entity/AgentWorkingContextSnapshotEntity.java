package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * WorkingContext 审计快照持久化实体。
 */
@Data
@TableName("agent_working_context_snapshot")
public class AgentWorkingContextSnapshotEntity {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** working context snapshot ID。 */
    private String workingContextSnapshotId;

    /** conversation ID。 */
    private String conversationId;

    /** session ID。 */
    private String sessionId;

    /** turn ID。 */
    private String turnId;

    /** run ID。 */
    private String runId;

    /** 当前 turn 内第几次构建上下文。 */
    private Integer contextBuildSeq;

    /** 当前 turn 内第几次主模型调用。 */
    private Integer modelCallSequenceNo;

    /** 触发本次构建的 CheckpointTrigger。 */
    private String checkpointTrigger;

    /** 使用者视图类型。 */
    private String contextViewType;

    /** 当前 agent 模式。 */
    private String agentMode;

    /** transcript 快照版本。 */
    private Long transcriptSnapshotVersion;

    /** working set 版本。 */
    private Long workingSetVersion;

    /** state 版本。 */
    private Long stateVersion;

    /** summary 版本。 */
    private Long summaryVersion;

    /** 预算快照 JSON。 */
    private String budgetJson;

    /** canonical block 集合 JSON。 */
    private String blockSetJson;

    /** canonical WorkingContext JSON。 */
    private String contextJson;

    /** 最终 projection JSON。 */
    private String projectionJson;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}

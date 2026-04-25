package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * state、summary、context 证据持久化实体。
 */
@Data
@TableName("agent_memory_evidence")
public class AgentMemoryEvidenceEntity {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** evidence ID。 */
    private String evidenceId;

    /** session ID。 */
    private String sessionId;

    /** turn ID。 */
    private String turnId;

    /** run ID。 */
    private String runId;

    /** 目标类型。 */
    private String targetType;

    /** 目标对象 ID。 */
    private String targetRef;

    /** 字段路径。 */
    private String fieldPath;

    /** 来源类型。 */
    private String sourceType;

    /** 来源 message ID。 */
    private String messageId;

    /** 来源 toolCallRecordId。 */
    private String toolCallRecordId;

    /** 来源 WorkingContextSnapshotId。 */
    private String workingContextSnapshotId;

    /** 来源 InternalTaskId。 */
    private String internalTaskId;

    /** 证据摘录。 */
    private String excerptText;

    /** 置信度。 */
    private BigDecimal confidence;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}

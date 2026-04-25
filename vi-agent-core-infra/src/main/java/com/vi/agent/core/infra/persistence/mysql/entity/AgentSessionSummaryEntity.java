package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * session 历史摘要持久化实体。
 */
@Data
@TableName("agent_session_summary")
public class AgentSessionSummaryEntity {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** summary ID。 */
    private String summaryId;

    /** session ID。 */
    private String sessionId;

    /** summary 版本号，单 session 单调递增。 */
    private Long summaryVersion;

    /** 覆盖起始 message sequence_no。 */
    private Long coveredFromSequenceNo;

    /** 覆盖结束 message sequence_no。 */
    private Long coveredToSequenceNo;

    /** 摘要正文。 */
    private String summaryText;

    /** summary prompt 模板 key。 */
    private String summaryTemplateKey;

    /** summary prompt 模板版本。 */
    private String summaryTemplateVersion;

    /** 生成 summary 的 provider。 */
    private String generatorProvider;

    /** 生成 summary 的 model。 */
    private String generatorModel;

    /** 触发本次 summary 的 run ID。 */
    private String sourceRunId;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}

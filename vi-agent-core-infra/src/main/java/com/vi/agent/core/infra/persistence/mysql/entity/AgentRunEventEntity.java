package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vi.agent.core.model.runtime.RunEventType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Run 事件持久化实体。
 */
@Data
@TableName("agent_run_event")
public class AgentRunEventEntity {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件ID。 */
    private String eventId;

    /** 会话窗口ID。 */
    private String conversationId;

    /** Session ID。 */
    private String sessionId;

    /** Turn ID。 */
    private String turnId;

    /** Run ID。 */
    private String runId;

    /** 事件顺序。 */
    private Integer eventIndex;

    /** 事件类型。 */
    private RunEventType eventType;

    /** Actor类型。 */
    private String actorType;

    /** Actor ID。 */
    private String actorId;

    /** 事件载荷JSON。 */
    private String payloadJson;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}

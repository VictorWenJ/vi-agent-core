package com.vi.agent.core.infra.persistence.mysql.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.MessageStatus;
import com.vi.agent.core.model.message.MessageType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息事实持久化实体。
 */
@Data
@TableName("agent_message")
public class AgentMessageEntity {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消息ID。 */
    private String messageId;

    /** 会话窗口ID。 */
    private String conversationId;

    /** Session ID。 */
    private String sessionId;

    /** Turn ID。 */
    private String turnId;

    /** Run ID。 */
    private String runId;

    /** 消息角色。 */
    private MessageRole role;

    /** 消息类型。 */
    private MessageType messageType;

    /** Session内序号。 */
    private Long sequenceNo;

    /** 消息状态。 */
    private MessageStatus status;

    /** 文本内容。 */
    private String contentText;

    /** 工具调用记录ID。 */
    private String toolCallRecordId;

    /** provider tool_call_id。 */
    private String toolCallId;

    /** 工具名称。 */
    private String toolName;

    /** provider 名称。 */
    private String provider;

    /** model 名称。 */
    private String model;

    /** 完成原因。 */
    private FinishReason finishReason;

    /** 元数据JSON。 */
    private String metadataJson;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}

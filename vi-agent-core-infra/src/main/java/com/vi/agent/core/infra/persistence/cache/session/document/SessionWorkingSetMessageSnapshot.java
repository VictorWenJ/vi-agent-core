package com.vi.agent.core.infra.persistence.cache.session.document;

import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.MessageStatus;
import com.vi.agent.core.model.message.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Session working set 内的 raw message 快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionWorkingSetMessageSnapshot {

    /** 消息 ID。 */
    private String messageId;

    /** conversation ID。 */
    private String conversationId;

    /** session ID。 */
    private String sessionId;

    /** turn ID。 */
    private String turnId;

    /** run ID。 */
    private String runId;

    /** 消息角色。 */
    private MessageRole role;

    /** 消息类型。 */
    private MessageType messageType;

    /** session 内消息序号。 */
    private Long sequenceNo;

    /** 消息状态。 */
    private MessageStatus status;

    /** 消息文本内容。 */
    private String contentText;

    /** 消息创建时间 epoch millis。 */
    private Long createdAtEpochMs;

    /** assistant 消息携带的 tool call 快照列表。 */
    private List<AssistantToolCallSnapshot> toolCalls;

    /** assistant finish reason。 */
    private String finishReason;

    /** 模型输入 token 数。 */
    private Integer usageInputTokens;

    /** 模型输出 token 数。 */
    private Integer usageOutputTokens;

    /** 模型总 token 数。 */
    private Integer usageTotalTokens;

    /** usage provider。 */
    private String usageProvider;

    /** usage model。 */
    private String usageModel;

    /** 工具调用记录 ID。 */
    private String toolCallRecordId;

    /** provider tool call ID。 */
    private String toolCallId;

    /** 工具名称。 */
    private String toolName;

    /** 工具执行状态。 */
    private String executionStatus;

    /** 工具错误码。 */
    private String errorCode;

    /** 工具错误信息。 */
    private String errorMessage;

    /** 工具耗时毫秒数。 */
    private Long durationMs;

    /** 工具调用参数 JSON。 */
    private String argumentsJson;
}
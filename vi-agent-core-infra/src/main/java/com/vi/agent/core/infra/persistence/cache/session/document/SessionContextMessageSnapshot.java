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
 * Session 上下文消息快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionContextMessageSnapshot {

    private String messageId;

    private String conversationId;

    private String sessionId;

    private String turnId;

    private String runId;

    private MessageRole role;

    private MessageType messageType;

    private Long sequenceNo;

    private MessageStatus status;

    private String contentText;

    private Long createdAtEpochMs;

    private List<AssistantToolCallSnapshot> toolCalls;

    private String finishReason;

    private Integer usageInputTokens;

    private Integer usageOutputTokens;

    private Integer usageTotalTokens;

    private String usageProvider;

    private String usageModel;

    private String toolCallRecordId;

    private String toolCallId;

    private String toolName;

    private String executionStatus;

    private String errorCode;

    private String errorMessage;

    private Long durationMs;

    private String argumentsJson;
}

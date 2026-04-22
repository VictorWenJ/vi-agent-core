package com.vi.agent.core.infra.persistence.cache.session.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 助手工具调用快照。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantToolCallSnapshot {

    private String toolCallRecordId;

    private String toolCallId;

    private String assistantMessageId;

    private String conversationId;

    private String sessionId;

    private String turnId;

    private String runId;

    private String toolName;

    private String argumentsJson;

    private Integer callIndex;

    private String status;

    private Long createdAtEpochMs;
}

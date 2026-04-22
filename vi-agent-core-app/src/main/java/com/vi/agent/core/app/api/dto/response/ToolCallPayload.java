package com.vi.agent.core.app.api.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 工具调用事件载荷。
 */
@Getter
@Builder
public class ToolCallPayload {

    private String toolCallRecordId;

    private String toolCallId;

    private String assistantMessageId;

    private String toolName;

    private String argumentsJson;

    private Integer callIndex;

    private String status;

    private Instant createdAt;
}

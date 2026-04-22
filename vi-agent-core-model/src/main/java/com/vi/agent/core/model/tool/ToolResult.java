package com.vi.agent.core.model.tool;

import lombok.Builder;
import lombok.Getter;

/**
 * 工具执行输出。
 */
@Getter
@Builder
public class ToolResult {

    private final String toolCallRecordId;

    private final String toolCallId;

    private final String toolName;

    private final String turnId;

    private final boolean success;

    private final String output;

    private final String errorCode;

    private final String errorMessage;

    private final Long durationMs;
}

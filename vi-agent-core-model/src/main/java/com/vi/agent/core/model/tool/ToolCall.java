package com.vi.agent.core.model.tool;

import lombok.Builder;
import lombok.Getter;

/**
 * 工具执行输入。
 */
@Getter
@Builder
public class ToolCall {

    private final String toolCallRecordId;

    private final String toolCallId;

    private final String toolName;

    private final String argumentsJson;

    private final String turnId;
}

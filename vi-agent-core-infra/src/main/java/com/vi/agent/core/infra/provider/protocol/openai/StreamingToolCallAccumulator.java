package com.vi.agent.core.infra.provider.protocol.openai;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 流式工具调用累积状态。
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StreamingToolCallAccumulator {

    /**
     * 工具调用 ID。
     */
    public String toolCallId;

    /**
     * 工具名称。
     */
    public String toolName;

    /**
     * 参数增量缓冲区。
     */
    public final StringBuilder argumentsBuilder = new StringBuilder();

    /**
     * 所属轮次 ID。
     */
    public String turnId;
}

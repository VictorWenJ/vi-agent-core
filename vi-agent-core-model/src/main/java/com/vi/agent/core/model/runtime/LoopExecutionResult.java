package com.vi.agent.core.model.runtime;

import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.tool.ToolCallRecord;
import com.vi.agent.core.model.tool.ToolResultRecord;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Agent loop execution result.
 */
@Getter
@Builder
public class LoopExecutionResult {

    private final AssistantMessage assistantMessage;

    private final List<Message> appendedMessages;

    private final List<ToolCallRecord> toolCalls;

    private final List<ToolResultRecord> toolResults;

    private final FinishReason finishReason;

    private final UsageInfo usage;
}

package com.vi.agent.core.model.runtime;

import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.tool.ToolExecution;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Agent loop 执行结果。
 */
@Getter
@Builder
public class LoopExecutionResult {

    private final AssistantMessage assistantMessage;

    private final List<Message> appendedMessages;

    private final List<AssistantToolCall> toolCalls;

    private final List<ToolExecution> toolExecutions;

    private final FinishReason finishReason;

    private final UsageInfo usage;
}

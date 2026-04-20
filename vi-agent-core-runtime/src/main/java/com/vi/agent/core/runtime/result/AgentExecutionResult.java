package com.vi.agent.core.runtime.result;

import com.vi.agent.core.model.llm.FinishReason;
import com.vi.agent.core.model.llm.UsageInfo;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.runtime.RunStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Runtime execution result.
 */
@Getter
@Builder
public class AgentExecutionResult {

    private final String requestId;

    private final RunStatus runStatus;

    private final String conversationId;

    private final String sessionId;

    private final String turnId;

    private final String userMessageId;

    private final String assistantMessageId;

    private final String runId;

    private final AssistantMessage finalAssistantMessage;

    private final FinishReason finishReason;

    private final UsageInfo usage;

    private final Instant createdAt;
}

package com.vi.agent.core.runtime.memory;

import com.vi.agent.core.model.context.AgentMode;
import lombok.Builder;
import lombok.Getter;

/**
 * Command for post-turn session memory update.
 */
@Getter
@Builder
public class SessionMemoryUpdateCommand {

    private final String conversationId;

    private final String sessionId;

    private final String turnId;

    private final String runId;

    private final String traceId;

    private final String currentUserMessageId;

    private final String assistantMessageId;

    private final String workingContextSnapshotId;

    private final AgentMode agentMode;
}

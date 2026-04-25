package com.vi.agent.core.runtime.memory.task;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.memory.InternalTaskType;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * Command for an internal memory task audit execution.
 */
@Getter
@Builder
public class InternalMemoryTaskCommand {

    private final InternalTaskType taskType;

    private final String conversationId;

    private final String sessionId;

    private final String turnId;

    private final String runId;

    private final String traceId;

    private final String currentUserMessageId;

    private final String assistantMessageId;

    private final String workingContextSnapshotId;

    private final AgentMode agentMode;

    @Singular("messageId")
    private final List<String> messageIds;

    private final Long currentStateVersion;
}

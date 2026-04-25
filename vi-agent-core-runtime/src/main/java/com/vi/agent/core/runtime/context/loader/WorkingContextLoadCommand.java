package com.vi.agent.core.runtime.context.loader;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.CheckpointReason;
import com.vi.agent.core.model.context.CheckpointTrigger;
import com.vi.agent.core.model.context.ContextViewType;
import com.vi.agent.core.model.message.UserMessage;
import lombok.Builder;
import lombok.Getter;

/**
 * Command for building the main-agent working context.
 */
@Getter
@Builder
public class WorkingContextLoadCommand {

    private final String conversationId;

    private final String sessionId;

    private final String turnId;

    private final String runId;

    private final UserMessage currentUserMessage;

    private final AgentMode agentMode;

    private final ContextViewType contextViewType;

    private final CheckpointTrigger checkpointTrigger;

    private final CheckpointReason checkpointReason;

    private final Integer modelCallSequenceNo;
}

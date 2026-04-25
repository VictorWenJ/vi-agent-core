package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.message.Message;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * Command for extracting a StateDelta from a completed turn transcript.
 */
@Getter
@Builder
public class StateDeltaExtractionCommand {

    private final String conversationId;

    private final String sessionId;

    private final String turnId;

    private final String runId;

    private final String traceId;

    private final AgentMode agentMode;

    private final SessionStateSnapshot currentState;

    private final ConversationSummary conversationSummary;

    @Singular("turnMessage")
    private final List<Message> turnMessages;

    private final String workingContextSnapshotId;
}

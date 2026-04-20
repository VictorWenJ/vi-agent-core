package com.vi.agent.core.model.runtime;

import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.tool.ToolDefinition;
import com.vi.agent.core.model.turn.Turn;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agent run context.
 */
@Getter
public class AgentRunContext {

    private final RunMetadata runMetadata;

    private final Conversation conversation;

    private final Session session;

    private final Turn turn;

    private final String userInput;

    private final List<Message> workingMessages;

    private final List<ToolDefinition> availableTools;

    private AgentRunState state;

    private int iteration;

    @Builder
    public AgentRunContext(
        RunMetadata runMetadata,
        Conversation conversation,
        Session session,
        Turn turn,
        String userInput,
        List<Message> workingMessages,
        List<ToolDefinition> availableTools,
        AgentRunState state,
        int iteration
    ) {
        this.runMetadata = runMetadata;
        this.conversation = conversation;
        this.session = session;
        this.turn = turn;
        this.userInput = userInput;
        this.workingMessages = workingMessages == null ? new ArrayList<>() : new ArrayList<>(workingMessages);
        this.availableTools = availableTools == null ? new ArrayList<>() : new ArrayList<>(availableTools);
        this.state = state == null ? AgentRunState.STARTED : state;
        this.iteration = iteration;
    }

    public void appendWorkingMessage(Message message) {
        if (message != null) {
            this.workingMessages.add(message);
        }
    }

    public void nextIteration() {
        this.iteration++;
    }

    public void markCompleted() {
        this.state = AgentRunState.COMPLETED;
    }

    public void markFailed() {
        this.state = AgentRunState.FAILED;
    }
}

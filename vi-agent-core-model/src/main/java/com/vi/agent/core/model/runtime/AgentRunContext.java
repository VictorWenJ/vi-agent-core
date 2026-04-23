package com.vi.agent.core.model.runtime;

import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.tool.ToolDefinition;
import com.vi.agent.core.model.tool.ToolExecution;
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

    private final List<AssistantToolCall> toolCalls;

    private final List<ToolExecution> toolExecutions;

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
        List<AssistantToolCall> toolCalls,
        List<ToolExecution> toolExecutions,
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
        this.toolCalls = toolCalls == null ? new ArrayList<>() : new ArrayList<>(toolCalls);
        this.toolExecutions = toolExecutions == null ? new ArrayList<>() : new ArrayList<>(toolExecutions);
        this.state = state == null ? AgentRunState.STARTED : state;
        this.iteration = iteration;
    }

    public void appendWorkingMessage(Message message) {
        if (message != null) {
            this.workingMessages.add(message);
        }
    }

    public void appendToolCall(AssistantToolCall toolCall) {
        if (toolCall != null) {
            this.toolCalls.add(toolCall);
        }
    }

    public void appendToolExecution(ToolExecution toolExecution) {
        if (toolExecution != null) {
            this.toolExecutions.add(toolExecution);
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

    public List<AssistantToolCall> getToolCalls() {
        return Collections.unmodifiableList(new ArrayList<>(toolCalls));
    }

    public List<ToolExecution> getToolExecutions() {
        return Collections.unmodifiableList(new ArrayList<>(toolExecutions));
    }
}

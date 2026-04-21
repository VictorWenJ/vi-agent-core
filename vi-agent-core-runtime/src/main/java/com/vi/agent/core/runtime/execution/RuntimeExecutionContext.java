package com.vi.agent.core.runtime.execution;

import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.runtime.LoopExecutionResult;
import com.vi.agent.core.model.runtime.RunMetadata;
import com.vi.agent.core.model.session.SessionResolutionResult;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.runtime.command.RuntimeExecuteCommand;
import com.vi.agent.core.runtime.event.RuntimeEvent;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.function.Consumer;

/**
 * 一次 Runtime 执行过程内的上下文容器。
 */
@Getter
public class RuntimeExecutionContext {

    private final RuntimeExecuteCommand command;

    private final boolean streaming;

    private final Consumer<RuntimeEvent> eventConsumer;

    private final Instant startedAt;

    @Setter
    private SessionResolutionResult resolution;

    @Setter
    private RunMetadata runMetadata;

    @Setter
    private UserMessage userMessage;

    @Setter
    private Turn turn;

    @Setter
    private AgentRunContext runContext;

    @Setter
    private LoopExecutionResult loopResult;

    private RuntimeExecutionContext(RuntimeExecuteCommand command, Consumer<RuntimeEvent> eventConsumer, boolean streaming) {
        this.command = command;
        this.streaming = streaming;
        this.eventConsumer = eventConsumer;
        this.startedAt = Instant.now();
    }

    public static RuntimeExecutionContext create(RuntimeExecuteCommand command, Consumer<RuntimeEvent> eventConsumer, boolean streaming) {
        return new RuntimeExecutionContext(command, eventConsumer, streaming);
    }

    public String requestId() {
        return command == null ? null : command.getRequestId();
    }

    public String conversationId() {
        if (resolution != null && resolution.getConversation() != null) {
            return resolution.getConversation().getConversationId();
        }
        return command == null ? null : command.getConversationId();
    }

    public String sessionId() {
        if (resolution != null && resolution.getSession() != null) {
            return resolution.getSession().getSessionId();
        }
        return command == null ? null : command.getSessionId();
    }

    public String turnId() {
        if (turn != null) {
            return turn.getTurnId();
        }
        return runMetadata == null ? null : runMetadata.getTurnId();
    }

    public String runId() {
        if (turn != null && StringUtils.isNotBlank(turn.getRunId())) {
            return turn.getRunId();
        }
        return runMetadata == null ? null : runMetadata.getRunId();
    }

    public boolean hasTurn() {
        return turn != null;
    }

    public boolean hasRunContext() {
        return runContext != null;
    }
}


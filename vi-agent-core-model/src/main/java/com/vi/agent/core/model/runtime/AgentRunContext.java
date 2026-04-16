package com.vi.agent.core.model.runtime;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.tool.ToolDefinition;
import com.vi.agent.core.model.transcript.ConversationTranscript;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agent 一次运行的上下文。
 */
public class AgentRunContext {

    /** 链路追踪 ID。 */
    private final String traceId;

    /** 运行 ID。 */
    private final String runId;

    /** 会话 ID。 */
    private final String sessionId;

    /** 会话链路 ID。 */
    private final String conversationId;

    /** 当前轮次 ID。 */
    private final String turnId;

    /** 当前轮用户输入。 */
    private final String userInput;

    /** 当前工作上下文消息列表。 */
    private final List<Message> workingMessages;

    /** 可用工具定义列表。 */
    private final List<ToolDefinition> availableTools;

    /** 对应会话 Transcript。 */
    private final ConversationTranscript transcript;

    /** 当前运行状态。 */
    private RunState runState;

    /** 当前迭代次数。 */
    private int iteration;

    public AgentRunContext(
        String traceId,
        String runId,
        String sessionId,
        String conversationId,
        String turnId,
        String userInput,
        List<Message> workingMessages,
        List<ToolDefinition> availableTools,
        ConversationTranscript transcript,
        RunState runState
    ) {
        this.traceId = traceId;
        this.runId = runId;
        this.sessionId = sessionId;
        this.conversationId = conversationId;
        this.turnId = turnId;
        this.userInput = userInput;
        this.workingMessages = new ArrayList<>(workingMessages);
        this.availableTools = availableTools == null ? new ArrayList<>() : new ArrayList<>(availableTools);
        this.transcript = transcript;
        this.runState = runState;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getRunId() {
        return runId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getTurnId() {
        return turnId;
    }

    public String getUserInput() {
        return userInput;
    }

    public List<Message> getWorkingMessages() {
        return Collections.unmodifiableList(workingMessages);
    }

    public void appendWorkingMessage(Message message) {
        if (message != null) {
            this.workingMessages.add(message);
        }
    }

    public List<ToolDefinition> getAvailableTools() {
        return Collections.unmodifiableList(availableTools);
    }

    public ConversationTranscript getTranscript() {
        return transcript;
    }

    public RunState getRunState() {
        return runState;
    }

    public void setRunState(RunState runState) {
        this.runState = runState;
    }

    public int getIteration() {
        return iteration;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }
}

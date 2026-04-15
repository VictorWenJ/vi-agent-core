package com.vi.agent.core.model.runtime;

import com.vi.agent.core.model.message.Message;
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

    /** 当前轮用户输入。 */
    private final String userInput;

    /** 本轮工作上下文消息列表。 */
    private final List<Message> workingMessages;

    /** 对应会话 Transcript。 */
    private final ConversationTranscript transcript;

    /** 当前运行状态。 */
    private RunState runState;

    public AgentRunContext(
        String traceId,
        String runId,
        String sessionId,
        String userInput,
        List<Message> workingMessages,
        ConversationTranscript transcript,
        RunState runState
    ) {
        this.traceId = traceId;
        this.runId = runId;
        this.sessionId = sessionId;
        this.userInput = userInput;
        this.workingMessages = new ArrayList<>(workingMessages);
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

    public String getUserInput() {
        return userInput;
    }

    public List<Message> getWorkingMessages() {
        return Collections.unmodifiableList(workingMessages);
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
}

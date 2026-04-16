package com.vi.agent.core.model.runtime;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.tool.ToolDefinition;
import com.vi.agent.core.model.transcript.ConversationTranscript;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 一次运行的上下文。
 */
@Setter
@Getter
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
    private AgentRunState agentRunState;

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
        AgentRunState agentRunState
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
        this.agentRunState = agentRunState;
    }

    public void appendWorkingMessage(Message message) {
        if (message != null) {
            this.workingMessages.add(message);
        }
    }
}

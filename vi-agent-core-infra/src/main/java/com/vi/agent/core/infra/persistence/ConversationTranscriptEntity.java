package com.vi.agent.core.infra.persistence;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Transcript 持久化实体（Phase 1 最小结构）。
 */
public class ConversationTranscriptEntity {

    /** 会话 ID。 */
    private String sessionId;

    /** 链路追踪 ID。 */
    private String traceId;

    /** 运行 ID。 */
    private String runId;

    /** 消息列表。 */
    private List<Message> messages = new ArrayList<>();

    /** 工具调用记录。 */
    private List<ToolCall> toolCalls = new ArrayList<>();

    /** 工具执行结果记录。 */
    private List<ToolResult> toolResults = new ArrayList<>();

    /** 更新时间。 */
    private Instant updatedAt = Instant.now();

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }

    public List<ToolResult> getToolResults() {
        return toolResults;
    }

    public void setToolResults(List<ToolResult> toolResults) {
        this.toolResults = toolResults;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

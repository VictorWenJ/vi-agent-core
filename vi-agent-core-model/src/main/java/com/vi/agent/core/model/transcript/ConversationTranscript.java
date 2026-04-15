package com.vi.agent.core.model.transcript;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolResult;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话 Transcript，保存完整会话历史与工具执行记录。
 */
@Getter
@NoArgsConstructor
public class ConversationTranscript {

    /** 会话 ID。 */
    private String sessionId;

    /** 链路追踪 ID。 */
    @Setter
    private String traceId;

    /** 运行 ID。 */
    @Setter
    private String runId;

    /** 会话消息列表。 */
    private final List<Message> messages = new ArrayList<>();

    /** 工具调用记录列表。 */
    private final List<ToolCall> toolCalls = new ArrayList<>();

    /** 工具执行结果列表。 */
    private final List<ToolResult> toolResults = new ArrayList<>();

    /** 最后更新时间。 */
    private Instant updatedAt = Instant.now();

    public ConversationTranscript(String sessionId) {
        this.sessionId = sessionId;
    }

    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public List<ToolCall> getToolCalls() {
        return Collections.unmodifiableList(toolCalls);
    }

    public List<ToolResult> getToolResults() {
        return Collections.unmodifiableList(toolResults);
    }

    public void appendMessage(Message message) {
        this.messages.add(message);
        this.updatedAt = Instant.now();
    }

    public void appendToolCall(ToolCall toolCall) {
        this.toolCalls.add(toolCall);
        this.updatedAt = Instant.now();
    }

    public void appendToolResult(ToolResult toolResult) {
        this.toolResults.add(toolResult);
        this.updatedAt = Instant.now();
    }

    public void replaceMessages(List<Message> sourceMessages) {
        this.messages.clear();
        if (sourceMessages != null) {
            this.messages.addAll(sourceMessages);
        }
        this.updatedAt = Instant.now();
    }

    public void replaceToolCalls(List<ToolCall> sourceToolCalls) {
        this.toolCalls.clear();
        if (sourceToolCalls != null) {
            this.toolCalls.addAll(sourceToolCalls);
        }
        this.updatedAt = Instant.now();
    }

    public void replaceToolResults(List<ToolResult> sourceToolResults) {
        this.toolResults.clear();
        if (sourceToolResults != null) {
            this.toolResults.addAll(sourceToolResults);
        }
        this.updatedAt = Instant.now();
    }
}


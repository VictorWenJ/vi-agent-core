package com.vi.agent.core.model.transcript;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话 Transcript，保存完整会话历史与工具执行记录。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationTranscript {

    /** 会话 ID。 */
    private String sessionId;

    /** 链路追踪 ID。 */
    private String traceId;

    /** 运行 ID。 */
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
        this.messages.addAll(sourceMessages);
    }

    public void replaceToolCalls(List<ToolCall> sourceToolCalls) {
        this.toolCalls.clear();
        this.toolCalls.addAll(sourceToolCalls);
    }

    public void replaceToolResults(List<ToolResult> sourceToolResults) {
        this.toolResults.clear();
        this.toolResults.addAll(sourceToolResults);
    }
}

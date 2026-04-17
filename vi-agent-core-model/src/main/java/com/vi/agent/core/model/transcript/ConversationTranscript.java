package com.vi.agent.core.model.transcript;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolResult;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话 Transcript，保存完整会话历史与工具执行记录。
 */
@Getter
public class ConversationTranscript {

    /** 会话 ID。 */
    private final String sessionId;

    /** 会话链路 ID。 */
    @Setter
    private String conversationId;

    /** 链路追踪 ID。 */
    @Setter
    private String traceId;

    /** 最后一次运行 ID。 */
    @Setter
    private String runId;

    /** 会话消息列表。 */
    private final List<Message> messages = new ArrayList<>();

    /** 工具调用记录列表。 */
    private final List<ToolCall> toolCalls = new ArrayList<>();

    /** 工具执行结果列表。 */
    private final List<ToolResult> toolResults = new ArrayList<>();

    /** 最后更新时间。 */
    private Instant updatedAt;

    private ConversationTranscript(String sessionId, String conversationId, Instant updatedAt) {
        this.sessionId = sessionId;
        this.conversationId = conversationId;
        this.updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    /**
     * 运行时新建 transcript。
     *
     * @param sessionId 会话 ID
     * @param conversationId 会话链路 ID
     * @return transcript
     */
    public static ConversationTranscript start(String sessionId, String conversationId) {
        return new ConversationTranscript(sessionId, conversationId, Instant.now());
    }

    /**
     * 持久化恢复 transcript。
     *
     * @param sessionId 会话 ID
     * @param conversationId 会话链路 ID
     * @param traceId traceId
     * @param runId runId
     * @param messages 消息列表
     * @param toolCalls 工具调用列表
     * @param toolResults 工具结果列表
     * @param updatedAt 更新时间
     * @return transcript
     */
    public static ConversationTranscript restore(
        String sessionId,
        String conversationId,
        String traceId,
        String runId,
        List<Message> messages,
        List<ToolCall> toolCalls,
        List<ToolResult> toolResults,
        Instant updatedAt
    ) {
        ConversationTranscript transcript = new ConversationTranscript(sessionId, conversationId, updatedAt);
        transcript.traceId = traceId;
        transcript.runId = runId;
        if (messages != null) {
            transcript.messages.addAll(messages);
        }
        if (toolCalls != null) {
            transcript.toolCalls.addAll(toolCalls);
        }
        if (toolResults != null) {
            transcript.toolResults.addAll(toolResults);
        }
        return transcript;
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
        if (message == null) {
            return;
        }
        this.messages.add(message);
        this.updatedAt = Instant.now();
    }

    public void appendToolCall(ToolCall toolCall) {
        if (toolCall == null) {
            return;
        }
        this.toolCalls.add(toolCall);
        this.updatedAt = Instant.now();
    }

    public void appendToolResult(ToolResult toolResult) {
        if (toolResult == null) {
            return;
        }
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

package com.vi.agent.core.infra.persistence;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Transcript 持久化实体（Phase 1 最小结构）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
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
}

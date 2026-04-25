package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.message.Message;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * 会话摘要抽取命令。
 */
@Getter
@Builder
public class ConversationSummaryExtractionCommand {

    /** 会话所属对话 ID。 */
    private final String conversationId;

    /** 会话 ID。 */
    private final String sessionId;

    /** 当前回合 ID。 */
    private final String turnId;

    /** 当前运行 ID。 */
    private final String runId;

    /** 当前链路追踪 ID。 */
    private final String traceId;

    /** 当前 Agent 模式。 */
    private final AgentMode agentMode;

    /** 最新会话摘要。 */
    private final ConversationSummary latestSummary;

    /** 最新会话状态快照。 */
    private final SessionStateSnapshot latestState;

    /** 当前回合已完成原始 transcript 消息。 */
    @Singular("turnMessage")
    private final List<Message> turnMessages;

    /** 构建本轮模型上下文的快照 ID。 */
    private final String workingContextSnapshotId;
}

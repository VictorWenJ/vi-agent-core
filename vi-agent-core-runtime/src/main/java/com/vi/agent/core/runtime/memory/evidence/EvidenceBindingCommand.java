package com.vi.agent.core.runtime.memory.evidence;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.memory.StateDelta;
import com.vi.agent.core.model.message.Message;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * Evidence 绑定命令。
 */
@Getter
@Builder
public class EvidenceBindingCommand {

    /** 对话 ID。 */
    private final String conversationId;

    /** 会话 ID。 */
    private final String sessionId;

    /** 回合 ID。 */
    private final String turnId;

    /** 运行 ID。 */
    private final String runId;

    /** 链路追踪 ID。 */
    private final String traceId;

    /** 工作上下文快照 ID。 */
    private final String workingContextSnapshotId;

    /** STATE_EXTRACT 内部任务 ID。 */
    private final String stateTaskId;

    /** SUMMARY_EXTRACT 内部任务 ID。 */
    private final String summaryTaskId;

    /** 更新前的 state 快照。 */
    private final SessionStateSnapshot latestState;

    /** 更新后的 state 快照。 */
    private final SessionStateSnapshot newState;

    /** 本轮成功写入 state 的状态增量。 */
    private final StateDelta stateDelta;

    /** 更新前的摘要。 */
    private final ConversationSummary latestSummary;

    /** 本轮成功写入的新摘要。 */
    private final ConversationSummary newSummary;

    /** 当前回合 completed raw transcript 消息。 */
    @Singular("turnMessage")
    private final List<Message> turnMessages;

    /** StateDelta 提供的候选来源 ID。 */
    @Singular("sourceCandidateId")
    private final List<String> sourceCandidateIds;

    /** 当前 Agent 模式。 */
    private final AgentMode agentMode;
}

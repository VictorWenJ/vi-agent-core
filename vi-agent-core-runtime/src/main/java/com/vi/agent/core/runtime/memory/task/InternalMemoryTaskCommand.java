package com.vi.agent.core.runtime.memory.task;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.memory.InternalTaskType;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * 内部记忆任务审计执行命令。
 */
@Getter
@Builder
public class InternalMemoryTaskCommand {

    /** 内部任务类型。 */
    private final InternalTaskType taskType;

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

    /** 当前用户消息 ID。 */
    private final String currentUserMessageId;

    /** 当前助手消息 ID。 */
    private final String assistantMessageId;

    /** 工作上下文快照 ID。 */
    private final String workingContextSnapshotId;

    /** Agent 模式。 */
    private final AgentMode agentMode;

    /** 内部任务输入涉及的 transcript message ID。 */
    @Singular("messageId")
    private final List<String> messageIds;

    /** 当前状态版本，保留给 STATE_EXTRACT 审计输入使用。 */
    private final Long currentStateVersion;

    /** 最新状态版本。 */
    private final Long latestStateVersion;

    /** 最新摘要版本。 */
    private final Long latestSummaryVersion;
}

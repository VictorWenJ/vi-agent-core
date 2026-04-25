package com.vi.agent.core.runtime.memory.task;

import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.InternalTaskStatus;
import com.vi.agent.core.model.memory.InternalTaskType;
import com.vi.agent.core.model.memory.StateDelta;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * 内部记忆任务审计执行结果。
 */
@Getter
@Builder(toBuilder = true)
public class InternalMemoryTaskResult {

    /** 内部任务 ID。 */
    private final String internalTaskId;

    /** 内部任务类型。 */
    private final InternalTaskType taskType;

    /** 内部任务状态。 */
    private final InternalTaskStatus status;

    /** 是否执行成功。 */
    private final boolean success;

    /** 是否发生降级。 */
    private final boolean degraded;

    /** 是否跳过执行。 */
    private final boolean skipped;

    /** STATE_EXTRACT 抽取出的状态增量。 */
    private final StateDelta stateDelta;

    /** SUMMARY_EXTRACT 生成的新摘要。 */
    private final ConversationSummary summary;

    /** 新状态版本。 */
    private final Long newStateVersion;

    /** 新摘要版本。 */
    private final Long newSummaryVersion;

    /** 状态增量候选来源 ID。 */
    @Singular("sourceCandidateId")
    private final List<String> sourceCandidateIds;

    /** 失败或降级原因。 */
    private final String failureReason;

    /** 内部任务输入 JSON。 */
    private final String inputJson;

    /** 内部任务输出 JSON。 */
    private final String outputJson;
}

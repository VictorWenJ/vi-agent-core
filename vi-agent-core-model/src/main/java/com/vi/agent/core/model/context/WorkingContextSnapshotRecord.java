package com.vi.agent.core.model.context;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * WorkingContext 审计快照记录。
 */
@Value
@Builder
@Jacksonized
public class WorkingContextSnapshotRecord {

    /** working context snapshot ID。 */
    String workingContextSnapshotId;

    /** conversation ID。 */
    String conversationId;

    /** session ID。 */
    String sessionId;

    /** turn ID。 */
    String turnId;

    /** run ID。 */
    String runId;

    /** 当前 turn 内第几次构建上下文。 */
    Integer contextBuildSeq;

    /** 当前 turn 内第几次主模型调用。 */
    Integer modelCallSequenceNo;

    /** 触发本次构建的 checkpoint trigger。 */
    CheckpointTrigger checkpointTrigger;

    /** 使用者视图类型。 */
    ContextViewType contextViewType;

    /** 当前 agent 模式。 */
    AgentMode agentMode;

    /** transcript 快照版本。 */
    Long transcriptSnapshotVersion;

    /** working set 版本。 */
    Long workingSetVersion;

    /** state 版本。 */
    Long stateVersion;

    /** summary 版本。 */
    Long summaryVersion;

    /** 预算快照 JSON。 */
    String budgetJson;

    /** canonical block 集合 JSON。 */
    String blockSetJson;

    /** canonical WorkingContext JSON。 */
    String contextJson;

    /** 最终 projection JSON。 */
    String projectionJson;

    /** 创建时间。 */
    Instant createdAt;
}

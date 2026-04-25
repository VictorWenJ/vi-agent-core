package com.vi.agent.core.model.context;

import lombok.Builder;
import lombok.Getter;

/**
 * WorkingContext 元数据。
 */
@Getter
@Builder
public class WorkingContextMetadata {

    /** WorkingContext 快照 ID。 */
    private final String workingContextSnapshotId;

    /** 会话所属 conversation ID。 */
    private final String conversationId;

    /** 当前 session ID。 */
    private final String sessionId;

    /** 当前 turn ID。 */
    private final String turnId;

    /** 当前 run ID。 */
    private final String runId;

    /** 本 session 内上下文构建序号。 */
    private final Integer contextBuildSeq;

    /** 当前 turn 内模型调用序号。 */
    private final Integer modelCallSequenceNo;

    /** 触发 checkpoint 的来源。 */
    private final CheckpointTrigger checkpointTrigger;

    /** 上下文视图类型。 */
    private final ContextViewType contextViewType;

    /** Agent 运行模式。 */
    private final AgentMode agentMode;
}

package com.vi.agent.core.model.memory;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * Evidence 的事实来源。
 */
@Getter
@Builder
@Jacksonized
public class EvidenceSource {

    /** evidence 来源类型。 */
    private final EvidenceSourceType sourceType;

    /** 来源 session ID。 */
    private final String sessionId;

    /** 来源 turn ID。 */
    private final String turnId;

    /** 来源 run ID。 */
    private final String runId;

    /** 来源消息 ID。 */
    private final String messageId;

    /** 来源工具调用记录 ID。 */
    private final String toolCallRecordId;

    /** 来源 WorkingContext 快照 ID。 */
    private final String workingContextSnapshotId;

    /** 来源内部任务 ID。 */
    private final String internalTaskId;

    /** 证据摘录文本。 */
    private final String excerptText;
}
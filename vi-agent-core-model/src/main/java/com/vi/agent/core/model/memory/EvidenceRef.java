package com.vi.agent.core.model.memory;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * state / summary / context audit 的证据引用。
 */
@Getter
@Builder
public class EvidenceRef {

    /** evidence ID。 */
    private final String evidenceId;

    /** 当前 session ID。 */
    private final String sessionId;

    /** 当前 turn ID。 */
    private final String turnId;

    /** 当前 run ID。 */
    private final String runId;

    /** evidence 指向目标。 */
    private final EvidenceTarget target;

    /** evidence 事实来源。 */
    private final EvidenceSource source;

    /** 证据摘录文本。 */
    private final String excerptText;

    /** 证据置信度。 */
    private final BigDecimal confidence;

    /** evidence 创建时间。 */
    private final Instant createdAt;
}

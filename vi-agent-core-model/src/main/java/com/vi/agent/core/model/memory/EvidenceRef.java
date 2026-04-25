package com.vi.agent.core.model.memory;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * state / summary / context audit 的证据引用。
 */
@Getter
@Builder
@Jacksonized
public class EvidenceRef {

    /** evidence ID。 */
    private final String evidenceId;

    /** evidence 指向目标。 */
    private final EvidenceTarget target;

    /** evidence 事实来源。 */
    private final EvidenceSource source;

    /** 证据置信度。 */
    private final BigDecimal confidence;

    /** evidence 创建时间。 */
    private final Instant createdAt;
}
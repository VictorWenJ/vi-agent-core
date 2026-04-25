package com.vi.agent.core.model.memory;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * Evidence 指向的目标对象。
 */
@Getter
@Builder
@Jacksonized
public class EvidenceTarget {

    /** evidence 指向的目标类型。 */
    private final EvidenceTargetType targetType;

    /** evidence 指向的目标对象 ID。 */
    private final String targetRef;

    /** 目标字段，作为稳定定位维度之一。 */
    private final String targetField;

    /** 目标字段内的稳定项 ID。 */
    private final String targetItemId;

    /** 展示路径，仅用于审计展示，不作为稳定主定位。 */
    private final String displayPath;
}
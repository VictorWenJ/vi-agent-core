package com.vi.agent.core.model.memory;

import lombok.Builder;
import lombok.Getter;

/**
 * Evidence 指向的目标对象。
 */
@Getter
@Builder
public class EvidenceTarget {

    /** evidence 指向的目标类型。 */
    private final EvidenceTargetType targetType;

    /** evidence 指向的目标引用。 */
    private final String targetRef;

    /** evidence 指向的目标字段路径。 */
    private final String fieldPath;
}

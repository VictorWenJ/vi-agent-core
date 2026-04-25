package com.vi.agent.core.model.context;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * WorkingContextProjection 校验结果。
 */
@Getter
@Builder
public class ProjectionValidationResult {

    /** 投影结果是否有效。 */
    private final boolean valid;

    /** 投影校验违规项列表。 */
    @Singular("violation")
    private final List<String> violations;

    public boolean hasViolation() {
        return !violations.isEmpty();
    }
}

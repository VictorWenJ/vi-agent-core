package com.vi.agent.core.model.context;

import lombok.Builder;
import lombok.Getter;

/**
 * WorkingContext 构建产物。
 */
@Getter
@Builder
public class WorkingContextBuildResult {

    /** 构建出的 canonical WorkingContext。 */
    private final WorkingContext context;

    /** provider-ready 上下文投影。 */
    private final WorkingContextProjection projection;

    /** 上下文装配计划。 */
    private final ContextPlan contextPlan;

    /** 投影校验结果。 */
    private final ProjectionValidationResult validationResult;
}

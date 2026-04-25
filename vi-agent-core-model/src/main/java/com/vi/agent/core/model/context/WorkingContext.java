package com.vi.agent.core.model.context;

import com.vi.agent.core.model.context.block.ContextBlockSet;
import lombok.Builder;
import lombok.Getter;

/**
 * 单次模型调用的 canonical context。
 */
@Getter
@Builder
public class WorkingContext {

    /** WorkingContext 元数据。 */
    private final WorkingContextMetadata metadata;

    /** WorkingContext 来源版本信息。 */
    private final WorkingContextSource source;

    /** 上下文 block 集合。 */
    private final ContextBlockSet blockSet;

    /** token 预算快照。 */
    private final ContextBudgetSnapshot budgetSnapshot;
}

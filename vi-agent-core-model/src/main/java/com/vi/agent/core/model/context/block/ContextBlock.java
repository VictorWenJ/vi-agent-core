package com.vi.agent.core.model.context.block;

import com.vi.agent.core.model.context.ContextAssemblyDecision;
import com.vi.agent.core.model.context.ContextBlockType;
import com.vi.agent.core.model.context.ContextPriority;
import lombok.Getter;

import java.util.List;

/**
 * WorkingContext 的基础装配单元。
 */
@Getter
public abstract class ContextBlock {

    /** block 唯一 ID。 */
    private final String blockId;

    /** block 类型。 */
    private final ContextBlockType blockType;

    /** block 装配优先级。 */
    private final ContextPriority priority;

    /** 是否为必选 block。 */
    private final boolean required;

    /** block 预计 token 数。 */
    private final Integer tokenEstimate;

    /** block 装配决策。 */
    private final ContextAssemblyDecision decision;

    /** block 来源引用列表。 */
    private final List<ContextSourceRef> sourceRefs;

    /** block 关联 evidence ID 列表。 */
    private final List<String> evidenceIds;

    protected ContextBlock(
        String blockId,
        ContextBlockType blockType,
        ContextPriority priority,
        boolean required,
        Integer tokenEstimate,
        ContextAssemblyDecision decision,
        List<ContextSourceRef> sourceRefs,
        List<String> evidenceIds
    ) {
        this.blockId = blockId;
        this.blockType = blockType;
        this.priority = priority;
        this.required = required;
        this.tokenEstimate = tokenEstimate;
        this.decision = decision;
        this.sourceRefs = sourceRefs == null || sourceRefs.isEmpty() ? List.of() : List.copyOf(sourceRefs);
        this.evidenceIds = evidenceIds == null || evidenceIds.isEmpty() ? List.of() : List.copyOf(evidenceIds);
    }
}

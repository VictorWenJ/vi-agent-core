package com.vi.agent.core.model.context.block;

import com.vi.agent.core.model.context.ContextAssemblyDecision;
import com.vi.agent.core.model.context.ContextBlockType;
import com.vi.agent.core.model.context.ContextPriority;
import com.vi.agent.core.model.memory.ContextReference;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 轻量上下文引用块。
 */
@Getter
public class ContextReferenceBlock extends ContextBlock {

    /** 轻量上下文引用列表。 */
    private final List<ContextReference> references;

    @Builder
    private ContextReferenceBlock(
        String blockId,
        ContextPriority priority,
        boolean required,
        Integer tokenEstimate,
        ContextAssemblyDecision decision,
        List<ContextSourceRef> sourceRefs,
        List<String> evidenceIds,
        List<ContextReference> references
    ) {
        super(blockId, ContextBlockType.CONTEXT_REFERENCE, priority, required, tokenEstimate, decision, sourceRefs, evidenceIds);
        this.references = references == null || references.isEmpty() ? List.of() : List.copyOf(references);
    }
}

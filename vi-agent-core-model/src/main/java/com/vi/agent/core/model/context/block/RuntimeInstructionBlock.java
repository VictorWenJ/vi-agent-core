package com.vi.agent.core.model.context.block;

import com.vi.agent.core.model.context.ContextAssemblyDecision;
import com.vi.agent.core.model.context.ContextBlockType;
import com.vi.agent.core.model.context.ContextPriority;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * S0 运行时指令块。
 */
@Getter
public class RuntimeInstructionBlock extends ContextBlock {

    /** prompt 模板 key。 */
    private final String promptTemplateKey;

    /** prompt 模板版本。 */
    private final String promptTemplateVersion;

    /** 渲染后的运行时指令文本。 */
    private final String renderedText;

    @Builder
    private RuntimeInstructionBlock(
        String blockId,
        ContextPriority priority,
        boolean required,
        Integer tokenEstimate,
        ContextAssemblyDecision decision,
        List<ContextSourceRef> sourceRefs,
        List<String> evidenceIds,
        String promptTemplateKey,
        String promptTemplateVersion,
        String renderedText
    ) {
        super(blockId, ContextBlockType.RUNTIME_INSTRUCTION, priority, required, tokenEstimate, decision, sourceRefs, evidenceIds);
        this.promptTemplateKey = promptTemplateKey;
        this.promptTemplateVersion = promptTemplateVersion;
        this.renderedText = renderedText;
    }
}

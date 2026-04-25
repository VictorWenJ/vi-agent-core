package com.vi.agent.core.model.context.block;

import com.vi.agent.core.model.context.ContextAssemblyDecision;
import com.vi.agent.core.model.context.ContextBlockType;
import com.vi.agent.core.model.context.ContextPriority;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 压缩动作说明块。
 */
@Getter
public class CompactionNoteBlock extends ContextBlock {

    /** 压缩动作说明文本。 */
    private final String noteText;

    /** 压缩结果 ID。 */
    private final String compactionResultId;

    @Builder
    private CompactionNoteBlock(
        String blockId,
        ContextPriority priority,
        boolean required,
        Integer tokenEstimate,
        ContextAssemblyDecision decision,
        List<ContextSourceRef> sourceRefs,
        List<String> evidenceIds,
        String noteText,
        String compactionResultId
    ) {
        super(blockId, ContextBlockType.COMPACTION_NOTE, priority, required, tokenEstimate, decision, sourceRefs, evidenceIds);
        this.noteText = noteText;
        this.compactionResultId = compactionResultId;
    }
}

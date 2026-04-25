package com.vi.agent.core.model.context.block;

import com.vi.agent.core.model.context.ContextAssemblyDecision;
import com.vi.agent.core.model.context.ContextBlockType;
import com.vi.agent.core.model.context.ContextPriority;
import com.vi.agent.core.model.memory.ConversationSummary;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * S2 会话摘要渲染块。
 */
@Getter
public class ConversationSummaryBlock extends ContextBlock {

    /** conversation summary 版本。 */
    private final Long summaryVersion;

    /** prompt 模板 key。 */
    private final String promptTemplateKey;

    /** prompt 模板版本。 */
    private final String promptTemplateVersion;

    /** 会话摘要对象。 */
    private final ConversationSummary summary;

    /** 渲染后的会话摘要文本。 */
    private final String renderedText;

    @Builder
    private ConversationSummaryBlock(
        String blockId,
        ContextPriority priority,
        boolean required,
        Integer tokenEstimate,
        ContextAssemblyDecision decision,
        List<ContextSourceRef> sourceRefs,
        List<String> evidenceIds,
        Long summaryVersion,
        String promptTemplateKey,
        String promptTemplateVersion,
        ConversationSummary summary,
        String renderedText
    ) {
        super(blockId, ContextBlockType.CONVERSATION_SUMMARY, priority, required, tokenEstimate, decision, sourceRefs, evidenceIds);
        this.summaryVersion = summaryVersion;
        this.promptTemplateKey = promptTemplateKey;
        this.promptTemplateVersion = promptTemplateVersion;
        this.summary = summary;
        this.renderedText = renderedText;
    }
}

package com.vi.agent.core.model.context.block;

import com.vi.agent.core.model.context.ContextAssemblyDecision;
import com.vi.agent.core.model.context.ContextBlockType;
import com.vi.agent.core.model.context.ContextPriority;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * S1 会话状态区块，承载结构化 SessionState 快照。
 */
@Getter
public class SessionStateBlock extends ContextBlock {

    /** 会话状态版本号。 */
    private final Long stateVersion;

    /** 系统提示词模板 key。 */
    private final String promptTemplateKey;

    /** 系统提示词模板版本。 */
    private final String promptTemplateVersion;

    /** 会话状态结构化快照。 */
    private final SessionStateSnapshot stateSnapshot;

    /** 渲染后的会话状态文本。 */
    private final String renderedText;

    @Builder
    private SessionStateBlock(
        String blockId,
        ContextPriority priority,
        boolean required,
        Integer tokenEstimate,
        ContextAssemblyDecision decision,
        List<ContextSourceRef> sourceRefs,
        List<String> evidenceIds,
        Long stateVersion,
        String promptTemplateKey,
        String promptTemplateVersion,
        SessionStateSnapshot stateSnapshot,
        String renderedText
    ) {
        super(blockId, ContextBlockType.SESSION_STATE, priority, required, tokenEstimate, decision, sourceRefs, evidenceIds);
        this.stateVersion = stateVersion;
        this.promptTemplateKey = promptTemplateKey;
        this.promptTemplateVersion = promptTemplateVersion;
        this.stateSnapshot = stateSnapshot;
        this.renderedText = renderedText;
    }
}

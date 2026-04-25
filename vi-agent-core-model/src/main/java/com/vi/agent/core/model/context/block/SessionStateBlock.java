package com.vi.agent.core.model.context.block;

import com.vi.agent.core.model.context.ContextAssemblyDecision;
import com.vi.agent.core.model.context.ContextBlockType;
import com.vi.agent.core.model.context.ContextPriority;
import com.vi.agent.core.model.memory.SessionWorkingSetSnapshot;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * S1 缁撴瀯鍖?session state 娓叉煋鍧椼€? */
@Getter
public class SessionStateBlock extends ContextBlock {

    /** session state 鐗堟湰銆?*/
    private final Long stateVersion;

    /** prompt 妯℃澘 key銆?*/
    private final String promptTemplateKey;

    /** prompt 妯℃澘鐗堟湰銆?*/
    private final String promptTemplateVersion;

    /** 缁撴瀯鍖?session state 蹇収銆?*/
    private final SessionWorkingSetSnapshot stateSnapshot;

    /** 娓叉煋鍚庣殑 session state 鏂囨湰銆?*/
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
        SessionWorkingSetSnapshot stateSnapshot,
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


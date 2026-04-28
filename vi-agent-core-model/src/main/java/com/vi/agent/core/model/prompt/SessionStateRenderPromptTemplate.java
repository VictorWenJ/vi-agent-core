package com.vi.agent.core.model.prompt;

import lombok.Getter;

import java.util.List;

/**
 * 会话状态上下文渲染模板。
 */
@Getter
public final class SessionStateRenderPromptTemplate extends AbstractPromptTemplate {

    /**
     * 构造会话状态上下文渲染模板。
     */
    public SessionStateRenderPromptTemplate(
        String textTemplate,
        List<PromptInputVariable> inputVariables,
        String description
    ) {
        super(
            SystemPromptKey.SESSION_STATE_RENDER,
            PromptPurpose.SESSION_STATE_RENDER,
            PromptRenderOutputType.TEXT,
            textTemplate,
            List.of(),
            inputVariables,
            null,
            description
        );
    }
}

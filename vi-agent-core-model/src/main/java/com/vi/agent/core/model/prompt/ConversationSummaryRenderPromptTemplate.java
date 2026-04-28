package com.vi.agent.core.model.prompt;

import lombok.Getter;

import java.util.List;

/**
 * 会话摘要上下文渲染模板。
 */
@Getter
public final class ConversationSummaryRenderPromptTemplate extends AbstractPromptTemplate {

    /**
     * 构造会话摘要上下文渲染模板。
     */
    public ConversationSummaryRenderPromptTemplate(
        String textTemplate,
        List<PromptInputVariable> inputVariables,
        String description
    ) {
        super(
            SystemPromptKey.CONVERSATION_SUMMARY_RENDER,
            PromptPurpose.CONVERSATION_SUMMARY_RENDER,
            PromptRenderOutputType.TEXT,
            textTemplate,
            List.of(),
            inputVariables,
            null,
            description
        );
    }
}

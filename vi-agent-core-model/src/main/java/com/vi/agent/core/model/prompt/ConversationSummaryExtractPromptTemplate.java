package com.vi.agent.core.model.prompt;

import lombok.Getter;

import java.util.List;

/**
 * 会话摘要抽取 prompt 模板。
 */
@Getter
public final class ConversationSummaryExtractPromptTemplate extends AbstractPromptTemplate {

    /**
     * 构造会话摘要抽取 prompt 模板。
     */
    public ConversationSummaryExtractPromptTemplate(
        List<PromptMessageTemplate> messageTemplates,
        List<PromptInputVariable> inputVariables,
        String description
    ) {
        super(
            SystemPromptKey.CONVERSATION_SUMMARY_EXTRACT,
            PromptPurpose.CONVERSATION_SUMMARY_EXTRACTION,
            PromptRenderOutputType.CHAT_MESSAGES,
            "",
            messageTemplates,
            inputVariables,
            StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT,
            description
        );
    }
}

package com.vi.agent.core.model.prompt;

import lombok.Getter;

import java.util.List;

/**
 * 状态增量抽取 prompt 模板。
 */
@Getter
public final class StateDeltaExtractPromptTemplate extends AbstractPromptTemplate {

    /**
     * 构造状态增量抽取 prompt 模板。
     */
    public StateDeltaExtractPromptTemplate(
        List<PromptMessageTemplate> messageTemplates,
        List<PromptInputVariable> inputVariables,
        String description
    ) {
        super(
            SystemPromptKey.STATE_DELTA_EXTRACT,
            PromptPurpose.STATE_DELTA_EXTRACTION,
            PromptRenderOutputType.CHAT_MESSAGES,
            "",
            messageTemplates,
            inputVariables,
            StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT,
            description
        );
    }
}

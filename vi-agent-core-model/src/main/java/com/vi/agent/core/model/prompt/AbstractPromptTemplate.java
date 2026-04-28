package com.vi.agent.core.model.prompt;

import lombok.Getter;

import java.util.List;

/**
 * 系统级 prompt 模板抽象基类。
 */
@Getter
public abstract class AbstractPromptTemplate {

    /** 系统级 prompt key。 */
    private final SystemPromptKey promptKey;

    /** prompt 用途。 */
    private final PromptPurpose purpose;

    /** 渲染输出形态。 */
    private final PromptRenderOutputType renderOutputType;

    /** TEXT 模板正文。 */
    private final String textTemplate;

    /** CHAT_MESSAGES 消息模板。 */
    private final List<PromptMessageTemplate> messageTemplates;

    /** 输入变量声明。 */
    private final List<PromptInputVariable> inputVariables;

    /** 结构化输出契约 key。 */
    private final StructuredLlmOutputContractKey structuredOutputContractKey;

    /** 模板说明。 */
    private final String description;

    /**
     * 构造不可变系统级 prompt 模板。
     */
    protected AbstractPromptTemplate(
        SystemPromptKey promptKey,
        PromptPurpose purpose,
        PromptRenderOutputType renderOutputType,
        String textTemplate,
        List<PromptMessageTemplate> messageTemplates,
        List<PromptInputVariable> inputVariables,
        StructuredLlmOutputContractKey structuredOutputContractKey,
        String description
    ) {
        this.promptKey = promptKey;
        this.purpose = purpose;
        this.renderOutputType = renderOutputType;
        this.textTemplate = textTemplate == null ? "" : textTemplate;
        this.messageTemplates = messageTemplates == null || messageTemplates.isEmpty()
            ? List.of()
            : List.copyOf(messageTemplates);
        this.inputVariables = inputVariables == null || inputVariables.isEmpty()
            ? List.of()
            : List.copyOf(inputVariables);
        this.structuredOutputContractKey = structuredOutputContractKey;
        this.description = description == null ? "" : description;
    }
}

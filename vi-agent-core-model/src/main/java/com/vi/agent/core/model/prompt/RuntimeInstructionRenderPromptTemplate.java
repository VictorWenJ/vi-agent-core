package com.vi.agent.core.model.prompt;

import lombok.Getter;

import java.util.List;

/**
 * 主聊天运行指令渲染模板。
 */
@Getter
public final class RuntimeInstructionRenderPromptTemplate extends AbstractPromptTemplate {

    /**
     * 构造主聊天运行指令渲染模板。
     */
    public RuntimeInstructionRenderPromptTemplate(
        String textTemplate,
        List<PromptInputVariable> inputVariables,
        String description
    ) {
        super(
            SystemPromptKey.RUNTIME_INSTRUCTION_RENDER,
            PromptPurpose.RUNTIME_INSTRUCTION_RENDER,
            PromptRenderOutputType.TEXT,
            textTemplate,
            List.of(),
            inputVariables,
            null,
            description
        );
    }
}

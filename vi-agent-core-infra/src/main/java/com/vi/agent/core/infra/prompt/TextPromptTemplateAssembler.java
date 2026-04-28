package com.vi.agent.core.infra.prompt;

import com.vi.agent.core.model.prompt.AbstractPromptTemplate;
import com.vi.agent.core.model.prompt.ConversationSummaryRenderPromptTemplate;
import com.vi.agent.core.model.prompt.RuntimeInstructionRenderPromptTemplate;
import com.vi.agent.core.model.prompt.SessionStateRenderPromptTemplate;
import com.vi.agent.core.model.prompt.SystemPromptKey;

/**
 * TEXT 类型系统 prompt 模板组装器。
 */
public class TextPromptTemplateAssembler {

    /**
     * 按固定 prompt key 组装 concrete TEXT 模板。
     */
    public AbstractPromptTemplate assemble(
        PromptManifestLoader.PromptManifest manifest,
        String textTemplate
    ) {
        SystemPromptKey promptKey = manifest.promptKey();
        if (promptKey == SystemPromptKey.RUNTIME_INSTRUCTION_RENDER) {
            return new RuntimeInstructionRenderPromptTemplate(
                textTemplate,
                manifest.inputVariables(),
                manifest.description()
            );
        }
        if (promptKey == SystemPromptKey.SESSION_STATE_RENDER) {
            return new SessionStateRenderPromptTemplate(
                textTemplate,
                manifest.inputVariables(),
                manifest.description()
            );
        }
        if (promptKey == SystemPromptKey.CONVERSATION_SUMMARY_RENDER) {
            return new ConversationSummaryRenderPromptTemplate(
                textTemplate,
                manifest.inputVariables(),
                manifest.description()
            );
        }
        throw new IllegalStateException("当前 prompt key 不支持 TEXT 模板: " + promptKey.getValue());
    }
}

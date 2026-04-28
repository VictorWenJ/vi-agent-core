package com.vi.agent.core.infra.prompt;

import com.vi.agent.core.model.prompt.AbstractPromptTemplate;
import com.vi.agent.core.model.prompt.PromptRenderOutputType;

/**
 * prompt 模板组装器注册表。
 */
public class PromptTemplateAssemblerRegistry {

    /** TEXT 模板组装器。 */
    private final TextPromptTemplateAssembler textPromptTemplateAssembler;

    /** CHAT_MESSAGES 模板组装器。 */
    private final ChatMessagesPromptTemplateAssembler chatMessagesPromptTemplateAssembler;

    /**
     * 构造默认 prompt 模板组装器注册表。
     */
    public PromptTemplateAssemblerRegistry() {
        this(new TextPromptTemplateAssembler(), new ChatMessagesPromptTemplateAssembler());
    }

    /**
     * 构造 prompt 模板组装器注册表。
     */
    public PromptTemplateAssemblerRegistry(
        TextPromptTemplateAssembler textPromptTemplateAssembler,
        ChatMessagesPromptTemplateAssembler chatMessagesPromptTemplateAssembler
    ) {
        this.textPromptTemplateAssembler = textPromptTemplateAssembler;
        this.chatMessagesPromptTemplateAssembler = chatMessagesPromptTemplateAssembler;
    }

    /**
     * 组装 TEXT 类型模板。
     */
    public AbstractPromptTemplate assembleText(
        PromptManifestLoader.PromptManifest manifest,
        String textTemplate
    ) {
        if (manifest.renderOutputType() != PromptRenderOutputType.TEXT) {
            throw new IllegalStateException("manifest renderOutputType 不是 TEXT");
        }
        return textPromptTemplateAssembler.assemble(manifest, textTemplate);
    }

    /**
     * 组装 CHAT_MESSAGES 类型模板。
     */
    public AbstractPromptTemplate assembleChatMessages(
        PromptManifestLoader.PromptManifest manifest,
        String systemTemplate,
        String userTemplate
    ) {
        if (manifest.renderOutputType() != PromptRenderOutputType.CHAT_MESSAGES) {
            throw new IllegalStateException("manifest renderOutputType 不是 CHAT_MESSAGES");
        }
        return chatMessagesPromptTemplateAssembler.assemble(manifest, systemTemplate, userTemplate);
    }
}

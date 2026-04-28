package com.vi.agent.core.infra.prompt;

import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.prompt.AbstractPromptTemplate;
import com.vi.agent.core.model.prompt.ConversationSummaryExtractPromptTemplate;
import com.vi.agent.core.model.prompt.PromptMessageTemplate;
import com.vi.agent.core.model.prompt.StateDeltaExtractPromptTemplate;
import com.vi.agent.core.model.prompt.SystemPromptKey;

import java.util.Comparator;
import java.util.List;

/**
 * CHAT_MESSAGES 类型系统 prompt 模板组装器。
 */
public class ChatMessagesPromptTemplateAssembler {

    /**
     * 按固定 prompt key 组装 concrete CHAT_MESSAGES 模板。
     */
    public AbstractPromptTemplate assemble(
        PromptManifestLoader.PromptManifest manifest,
        String systemTemplate,
        String userTemplate
    ) {
        List<PromptMessageTemplate> messageTemplates = List.of(
                PromptMessageTemplate.builder()
                    .order(1)
                    .role(MessageRole.SYSTEM)
                    .contentTemplate(systemTemplate)
                    .build(),
                PromptMessageTemplate.builder()
                    .order(2)
                    .role(MessageRole.USER)
                    .contentTemplate(userTemplate)
                    .build()
            )
            .stream()
            .sorted(Comparator.comparing(PromptMessageTemplate::getOrder))
            .toList();

        SystemPromptKey promptKey = manifest.promptKey();
        if (promptKey == SystemPromptKey.STATE_DELTA_EXTRACT) {
            return new StateDeltaExtractPromptTemplate(
                messageTemplates,
                manifest.inputVariables(),
                manifest.description()
            );
        }
        if (promptKey == SystemPromptKey.CONVERSATION_SUMMARY_EXTRACT) {
            return new ConversationSummaryExtractPromptTemplate(
                messageTemplates,
                manifest.inputVariables(),
                manifest.description()
            );
        }
        throw new IllegalStateException("当前 prompt key 不支持 CHAT_MESSAGES 模板: " + promptKey.getValue());
    }
}

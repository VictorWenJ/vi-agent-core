package com.vi.agent.core.runtime.prompt;

import com.vi.agent.core.model.port.SystemPromptCatalogRepository;
import com.vi.agent.core.model.prompt.AbstractPromptTemplate;
import com.vi.agent.core.model.prompt.ConversationSummaryExtractPromptTemplate;
import com.vi.agent.core.model.prompt.ConversationSummaryRenderPromptTemplate;
import com.vi.agent.core.model.prompt.PromptPurpose;
import com.vi.agent.core.model.prompt.PromptRenderOutputType;
import com.vi.agent.core.model.prompt.RuntimeInstructionRenderPromptTemplate;
import com.vi.agent.core.model.prompt.SessionStateRenderPromptTemplate;
import com.vi.agent.core.model.prompt.StateDeltaExtractPromptTemplate;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.SystemPromptKey;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 系统 prompt 运行期只读注册表工厂。
 */
public class SystemPromptRegistryFactory {

    /** 系统 prompt catalog 读取端口。 */
    private final SystemPromptCatalogRepository catalogRepository;

    /**
     * 构造系统 prompt 注册表工厂。
     */
    public SystemPromptRegistryFactory(SystemPromptCatalogRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
    }

    /**
     * 从 catalog 仓储加载全部系统模板并构造只读注册表。
     */
    public SystemPromptRegistry create() {
        Map<SystemPromptKey, AbstractPromptTemplate> templates = new EnumMap<>(SystemPromptKey.class);
        Map<StructuredLlmOutputContractKey, StructuredLlmOutputContract> contracts =
            new EnumMap<>(StructuredLlmOutputContractKey.class);
        Map<SystemPromptKey, String> templateContentHashes = new EnumMap<>(SystemPromptKey.class);
        Map<SystemPromptKey, String> manifestContentHashes = new EnumMap<>(SystemPromptKey.class);
        Map<StructuredLlmOutputContractKey, String> contractContentHashes =
            new EnumMap<>(StructuredLlmOutputContractKey.class);
        Set<StructuredLlmOutputContractKey> extractContractKeys = new LinkedHashSet<>();

        for (SystemPromptKey promptKey : SystemPromptKey.values()) {
            AbstractPromptTemplate template = catalogRepository.findTemplate(promptKey)
                .orElseThrow(() -> new IllegalStateException("系统 prompt 模板缺失: " + promptKey.getValue()));
            validateTemplate(promptKey, template);
            templates.put(promptKey, template);
            templateContentHashes.put(promptKey, catalogRepository.templateContentHash(promptKey));
            manifestContentHashes.put(promptKey, catalogRepository.manifestContentHash(promptKey));

            StructuredLlmOutputContractKey contractKey = template.getStructuredOutputContractKey();
            if (contractKey != null) {
                if (!extractContractKeys.add(contractKey)) {
                    throw new IllegalStateException("extract prompt 结构化输出契约 key 重复: " + contractKey.getValue());
                }
                StructuredLlmOutputContract contract = catalogRepository.findContract(contractKey)
                    .orElseThrow(() -> new IllegalStateException("extract prompt 缺少结构化输出契约: " + contractKey.getValue()));
                contracts.put(contractKey, contract);
                contractContentHashes.put(contractKey, catalogRepository.contractContentHash(contractKey));
            }
        }

        return new DefaultSystemPromptRegistry(
            templates,
            contracts,
            templateContentHashes,
            manifestContentHashes,
            contractContentHashes,
            catalogRepository.catalogRevision()
        );
    }

    private void validateTemplate(SystemPromptKey expectedPromptKey, AbstractPromptTemplate template) {
        if (template.getPromptKey() != expectedPromptKey) {
            throw new IllegalStateException("模板 promptKey 与加载 key 不一致: " + expectedPromptKey.getValue());
        }
        if (template.getPurpose() != fixedPurpose(expectedPromptKey)) {
            throw new IllegalStateException("模板 purpose 与固定映射不一致: " + expectedPromptKey.getValue());
        }
        if (template.getRenderOutputType() != fixedRenderOutputType(expectedPromptKey)) {
            throw new IllegalStateException("模板 renderOutputType 与固定映射不一致: " + expectedPromptKey.getValue());
        }
        validateConcreteClass(expectedPromptKey, template);
        if (template.getRenderOutputType() == PromptRenderOutputType.TEXT
            && template.getStructuredOutputContractKey() != null) {
            throw new IllegalStateException("TEXT 模板不得绑定结构化输出契约: " + expectedPromptKey.getValue());
        }
        if (template.getRenderOutputType() == PromptRenderOutputType.CHAT_MESSAGES
            && template.getStructuredOutputContractKey() == null) {
            throw new IllegalStateException("extract 模板必须绑定结构化输出契约: " + expectedPromptKey.getValue());
        }
    }

    private void validateConcreteClass(SystemPromptKey expectedPromptKey, AbstractPromptTemplate template) {
        boolean matched = switch (expectedPromptKey) {
            case RUNTIME_INSTRUCTION_RENDER -> template instanceof RuntimeInstructionRenderPromptTemplate;
            case SESSION_STATE_RENDER -> template instanceof SessionStateRenderPromptTemplate;
            case CONVERSATION_SUMMARY_RENDER -> template instanceof ConversationSummaryRenderPromptTemplate;
            case STATE_DELTA_EXTRACT -> template instanceof StateDeltaExtractPromptTemplate;
            case CONVERSATION_SUMMARY_EXTRACT -> template instanceof ConversationSummaryExtractPromptTemplate;
        };
        if (!matched) {
            throw new IllegalStateException("模板 concrete class 与固定槽位不一致: " + expectedPromptKey.getValue());
        }
    }

    private PromptPurpose fixedPurpose(SystemPromptKey promptKey) {
        return switch (promptKey) {
            case RUNTIME_INSTRUCTION_RENDER -> PromptPurpose.RUNTIME_INSTRUCTION_RENDER;
            case SESSION_STATE_RENDER -> PromptPurpose.SESSION_STATE_RENDER;
            case CONVERSATION_SUMMARY_RENDER -> PromptPurpose.CONVERSATION_SUMMARY_RENDER;
            case STATE_DELTA_EXTRACT -> PromptPurpose.STATE_DELTA_EXTRACTION;
            case CONVERSATION_SUMMARY_EXTRACT -> PromptPurpose.CONVERSATION_SUMMARY_EXTRACTION;
        };
    }

    private PromptRenderOutputType fixedRenderOutputType(SystemPromptKey promptKey) {
        return switch (promptKey) {
            case RUNTIME_INSTRUCTION_RENDER,
                SESSION_STATE_RENDER,
                CONVERSATION_SUMMARY_RENDER -> PromptRenderOutputType.TEXT;
            case STATE_DELTA_EXTRACT,
                CONVERSATION_SUMMARY_EXTRACT -> PromptRenderOutputType.CHAT_MESSAGES;
        };
    }
}

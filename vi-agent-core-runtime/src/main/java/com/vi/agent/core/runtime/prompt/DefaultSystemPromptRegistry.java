package com.vi.agent.core.runtime.prompt;

import com.vi.agent.core.model.prompt.AbstractPromptTemplate;
import com.vi.agent.core.model.prompt.ConversationSummaryExtractPromptTemplate;
import com.vi.agent.core.model.prompt.ConversationSummaryRenderPromptTemplate;
import com.vi.agent.core.model.prompt.RuntimeInstructionRenderPromptTemplate;
import com.vi.agent.core.model.prompt.SessionStateRenderPromptTemplate;
import com.vi.agent.core.model.prompt.StateDeltaExtractPromptTemplate;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.SystemPromptKey;

import java.util.Map;

/**
 * 默认系统 prompt 运行期只读注册表。
 */
public class DefaultSystemPromptRegistry implements SystemPromptRegistry {

    /** 系统 prompt 模板只读映射。 */
    private final Map<SystemPromptKey, AbstractPromptTemplate> templates;

    /** 结构化输出契约只读映射。 */
    private final Map<StructuredLlmOutputContractKey, StructuredLlmOutputContract> contracts;

    /** 模板正文内容 hash 映射。 */
    private final Map<SystemPromptKey, String> templateContentHashes;

    /** manifest 内容 hash 映射。 */
    private final Map<SystemPromptKey, String> manifestContentHashes;

    /** contract 内容 hash 映射。 */
    private final Map<StructuredLlmOutputContractKey, String> contractContentHashes;

    /** catalog 修订标识。 */
    private final String catalogRevision;

    /**
     * 构造系统 prompt 运行期只读注册表。
     */
    public DefaultSystemPromptRegistry(
        Map<SystemPromptKey, AbstractPromptTemplate> templates,
        Map<StructuredLlmOutputContractKey, StructuredLlmOutputContract> contracts,
        Map<SystemPromptKey, String> templateContentHashes,
        Map<SystemPromptKey, String> manifestContentHashes,
        Map<StructuredLlmOutputContractKey, String> contractContentHashes,
        String catalogRevision
    ) {
        this.templates = Map.copyOf(templates);
        this.contracts = Map.copyOf(contracts);
        this.templateContentHashes = Map.copyOf(templateContentHashes);
        this.manifestContentHashes = Map.copyOf(manifestContentHashes);
        this.contractContentHashes = Map.copyOf(contractContentHashes);
        this.catalogRevision = catalogRevision;
    }

    @Override
    public AbstractPromptTemplate get(SystemPromptKey promptKey) {
        AbstractPromptTemplate template = templates.get(promptKey);
        if (template == null) {
            throw new PromptRenderException("系统 prompt 模板不存在: " + promptKey.getValue());
        }
        return template;
    }

    @Override
    public RuntimeInstructionRenderPromptTemplate getRuntimeInstructionRenderPromptTemplate() {
        return (RuntimeInstructionRenderPromptTemplate) get(SystemPromptKey.RUNTIME_INSTRUCTION_RENDER);
    }

    @Override
    public SessionStateRenderPromptTemplate getSessionStateRenderPromptTemplate() {
        return (SessionStateRenderPromptTemplate) get(SystemPromptKey.SESSION_STATE_RENDER);
    }

    @Override
    public ConversationSummaryRenderPromptTemplate getConversationSummaryRenderPromptTemplate() {
        return (ConversationSummaryRenderPromptTemplate) get(SystemPromptKey.CONVERSATION_SUMMARY_RENDER);
    }

    @Override
    public StateDeltaExtractPromptTemplate getStateDeltaExtractPromptTemplate() {
        return (StateDeltaExtractPromptTemplate) get(SystemPromptKey.STATE_DELTA_EXTRACT);
    }

    @Override
    public ConversationSummaryExtractPromptTemplate getConversationSummaryExtractPromptTemplate() {
        return (ConversationSummaryExtractPromptTemplate) get(SystemPromptKey.CONVERSATION_SUMMARY_EXTRACT);
    }

    @Override
    public StructuredLlmOutputContract getStructuredLlmOutputContract(
        StructuredLlmOutputContractKey contractKey
    ) {
        StructuredLlmOutputContract contract = contracts.get(contractKey);
        if (contract == null) {
            throw new PromptRenderException("结构化输出契约不存在: " + contractKey.getValue());
        }
        return contract;
    }

    @Override
    public String templateContentHash(SystemPromptKey promptKey) {
        return templateContentHashes.get(promptKey);
    }

    @Override
    public String manifestContentHash(SystemPromptKey promptKey) {
        return manifestContentHashes.get(promptKey);
    }

    @Override
    public String contractContentHash(StructuredLlmOutputContractKey contractKey) {
        return contractKey == null ? null : contractContentHashes.get(contractKey);
    }

    @Override
    public String catalogRevision() {
        return catalogRevision;
    }

    /**
     * 返回模板只读映射，仅用于测试不可变快照。
     */
    Map<SystemPromptKey, AbstractPromptTemplate> templates() {
        return templates;
    }
}

package com.vi.agent.core.runtime.memory.task;

import com.vi.agent.core.model.llm.StructuredOutputChannelResult;
import com.vi.agent.core.model.memory.InternalTaskDefinitionKey;
import com.vi.agent.core.model.memory.InternalTaskType;
import com.vi.agent.core.model.prompt.PromptRenderMetadata;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import com.vi.agent.core.model.prompt.SystemPromptKey;
import com.vi.agent.core.runtime.prompt.SystemPromptRegistry;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内部记忆任务到 prompt catalog / deterministic task key 的解析器。
 */
@Component
public class InternalTaskPromptResolver {

    /** 本地测试默认 catalog 修订标识。 */
    private static final String LOCAL_DEV_REVISION = "local-dev";

    /** 系统 prompt 注册表，可为单元测试空值。 */
    private final SystemPromptRegistry systemPromptRegistry;

    /**
     * 构造内部任务 prompt 解析器。
     */
    public InternalTaskPromptResolver(SystemPromptRegistry systemPromptRegistry) {
        this.systemPromptRegistry = systemPromptRegistry;
    }

    /**
     * 解析内部任务审计 key。
     */
    public String resolvePromptTemplateKey(InternalTaskType taskType) {
        if (taskType == InternalTaskType.STATE_EXTRACT) {
            return SystemPromptKey.STATE_DELTA_EXTRACT.getValue();
        }
        if (taskType == InternalTaskType.SUMMARY_EXTRACT) {
            return SystemPromptKey.CONVERSATION_SUMMARY_EXTRACT.getValue();
        }
        if (taskType == InternalTaskType.EVIDENCE_ENRICH) {
            return InternalTaskDefinitionKey.EVIDENCE_BIND_DETERMINISTIC.getValue();
        }
        return "internal_memory_task_noop";
    }

    /**
     * 解析内部任务审计版本，P2-E 后统一使用 catalogRevision。
     */
    public String resolvePromptTemplateVersion(InternalTaskType taskType) {
        return catalogRevision();
    }

    /**
     * 构造 requestJson.promptAudit 字段。
     */
    public Map<String, Object> promptAudit(
        InternalTaskType taskType,
        PromptRenderMetadata renderMetadata,
        StructuredOutputChannelResult channelResult,
        String failureReason
    ) {
        Map<String, Object> audit = new LinkedHashMap<>();
        SystemPromptKey promptKey = promptKey(taskType);
        StructuredLlmOutputContractKey contractKey = contractKey(taskType);
        audit.put("promptKey", renderMetadata == null
            ? resolvePromptTemplateKey(taskType)
            : renderMetadata.getPromptKey().getValue());
        audit.put("structuredOutputContractKey", renderMetadata == null
            ? valueOf(contractKey)
            : valueOf(renderMetadata.getStructuredOutputContractKey()));
        audit.put("templateContentHash", renderMetadata == null ? templateContentHash(promptKey) : renderMetadata.getTemplateContentHash());
        audit.put("manifestContentHash", renderMetadata == null ? manifestContentHash(promptKey) : renderMetadata.getManifestContentHash());
        audit.put("contractContentHash", renderMetadata == null ? contractContentHash(contractKey) : renderMetadata.getContractContentHash());
        audit.put("catalogRevision", renderMetadata == null ? catalogRevision() : renderMetadata.getCatalogRevision());
        audit.put("actualStructuredOutputMode", channelResult == null ? null : valueOf(channelResult.getActualStructuredOutputMode()));
        audit.put("retryCount", channelResult == null || channelResult.getRetryCount() == null ? 0 : channelResult.getRetryCount());
        audit.put("failureReason", failureReason == null && channelResult != null ? channelResult.getFailureReason() : failureReason);
        return audit;
    }

    /**
     * 解析系统 prompt key。
     */
    private SystemPromptKey promptKey(InternalTaskType taskType) {
        if (taskType == InternalTaskType.STATE_EXTRACT) {
            return SystemPromptKey.STATE_DELTA_EXTRACT;
        }
        if (taskType == InternalTaskType.SUMMARY_EXTRACT) {
            return SystemPromptKey.CONVERSATION_SUMMARY_EXTRACT;
        }
        return null;
    }

    /**
     * 解析结构化输出契约 key。
     */
    private StructuredLlmOutputContractKey contractKey(InternalTaskType taskType) {
        if (taskType == InternalTaskType.STATE_EXTRACT) {
            return StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT;
        }
        if (taskType == InternalTaskType.SUMMARY_EXTRACT) {
            return StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT;
        }
        return null;
    }

    /**
     * 获取模板内容 hash。
     */
    private String templateContentHash(SystemPromptKey promptKey) {
        return systemPromptRegistry == null || promptKey == null ? null : systemPromptRegistry.templateContentHash(promptKey);
    }

    /**
     * 获取 manifest 内容 hash。
     */
    private String manifestContentHash(SystemPromptKey promptKey) {
        return systemPromptRegistry == null || promptKey == null ? null : systemPromptRegistry.manifestContentHash(promptKey);
    }

    /**
     * 获取 contract 内容 hash。
     */
    private String contractContentHash(StructuredLlmOutputContractKey contractKey) {
        return systemPromptRegistry == null || contractKey == null ? null : systemPromptRegistry.contractContentHash(contractKey);
    }

    /**
     * 获取 catalog 修订标识。
     */
    private String catalogRevision() {
        return systemPromptRegistry == null ? LOCAL_DEV_REVISION : systemPromptRegistry.catalogRevision();
    }

    /**
     * 读取结构化输出模式稳定 value。
     */
    private String valueOf(StructuredLlmOutputMode mode) {
        return mode == null ? null : mode.getValue();
    }

    /**
     * 读取结构化输出契约稳定 value。
     */
    private String valueOf(StructuredLlmOutputContractKey contractKey) {
        return contractKey == null ? null : contractKey.getValue();
    }
}

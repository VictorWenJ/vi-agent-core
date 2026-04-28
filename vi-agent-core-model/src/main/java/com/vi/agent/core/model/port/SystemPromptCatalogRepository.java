package com.vi.agent.core.model.port;

import com.vi.agent.core.model.prompt.AbstractPromptTemplate;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.SystemPromptKey;

import java.util.Optional;

/**
 * 系统级 prompt catalog 读取端口。
 */
public interface SystemPromptCatalogRepository {

    /**
     * 按系统 prompt key 查询模板。
     */
    Optional<AbstractPromptTemplate> findTemplate(SystemPromptKey promptKey);

    /**
     * 按结构化输出契约 key 查询契约。
     */
    Optional<StructuredLlmOutputContract> findContract(StructuredLlmOutputContractKey contractKey);

    /**
     * 查询模板正文内容摘要。
     */
    String templateContentHash(SystemPromptKey promptKey);

    /**
     * 查询模板 manifest 内容摘要。
     */
    String manifestContentHash(SystemPromptKey promptKey);

    /**
     * 查询结构化输出契约内容摘要。
     */
    String contractContentHash(StructuredLlmOutputContractKey contractKey);

    /**
     * 查询当前 catalog 修订标识。
     */
    String catalogRevision();
}

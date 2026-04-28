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

import java.util.Optional;

/**
 * 系统 prompt 运行期只读注册表。
 */
public interface SystemPromptRegistry {

    /**
     * 按系统 prompt key 获取模板。
     */
    AbstractPromptTemplate get(SystemPromptKey promptKey);

    /**
     * 获取主聊天运行指令模板。
     */
    RuntimeInstructionRenderPromptTemplate runtimeInstructionRenderTemplate();

    /**
     * 获取会话状态渲染模板。
     */
    SessionStateRenderPromptTemplate sessionStateRenderTemplate();

    /**
     * 获取会话摘要渲染模板。
     */
    ConversationSummaryRenderPromptTemplate conversationSummaryRenderTemplate();

    /**
     * 获取状态增量抽取模板。
     */
    StateDeltaExtractPromptTemplate stateDeltaExtractTemplate();

    /**
     * 获取会话摘要抽取模板。
     */
    ConversationSummaryExtractPromptTemplate conversationSummaryExtractTemplate();

    /**
     * 按结构化输出契约 key 获取契约。
     */
    Optional<StructuredLlmOutputContract> getStructuredLlmOutputContract(StructuredLlmOutputContractKey contractKey);

    /**
     * 获取模板正文内容 hash。
     */
    String templateContentHash(SystemPromptKey promptKey);

    /**
     * 获取 manifest 内容 hash。
     */
    String manifestContentHash(SystemPromptKey promptKey);

    /**
     * 获取 contract 内容 hash。
     */
    String contractContentHash(StructuredLlmOutputContractKey contractKey);

    /**
     * 获取 catalog 修订标识。
     */
    String catalogRevision();
}

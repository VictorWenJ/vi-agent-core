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

/**
 * 系统 prompt 运行期只读注册表。
 */
public interface SystemPromptRegistry {

    /**
     * 按系统 prompt key 获取模板。
     */
    AbstractPromptTemplate get(SystemPromptKey promptKey);

    /**
     * 获取主聊天运行指令渲染模板。
     */
    RuntimeInstructionRenderPromptTemplate getRuntimeInstructionRenderPromptTemplate();

    /**
     * 获取会话状态渲染模板。
     */
    SessionStateRenderPromptTemplate getSessionStateRenderPromptTemplate();

    /**
     * 获取会话摘要渲染模板。
     */
    ConversationSummaryRenderPromptTemplate getConversationSummaryRenderPromptTemplate();

    /**
     * 获取状态增量抽取模板。
     */
    StateDeltaExtractPromptTemplate getStateDeltaExtractPromptTemplate();

    /**
     * 获取会话摘要抽取模板。
     */
    ConversationSummaryExtractPromptTemplate getConversationSummaryExtractPromptTemplate();

    /**
     * 按结构化输出契约 key 获取契约。
     */
    StructuredLlmOutputContract getStructuredLlmOutputContract(StructuredLlmOutputContractKey contractKey);

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

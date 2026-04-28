package com.vi.agent.core.runtime.prompt;

import com.vi.agent.core.model.prompt.PromptPurpose;
import com.vi.agent.core.model.prompt.PromptRenderMetadata;
import com.vi.agent.core.model.prompt.PromptRenderOutputType;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.SystemPromptKey;
import lombok.Getter;

import java.util.List;

/**
 * CHAT_MESSAGES prompt 渲染结果。
 */
@Getter
public final class ChatMessagesPromptRenderResult extends AbstractPromptRenderResult {

    /** 渲染后的消息列表。 */
    private final List<PromptRenderedMessage> renderedMessages;

    /** 结构化输出契约 key。 */
    private final StructuredLlmOutputContractKey structuredOutputContractKey;

    /**
     * 构造 CHAT_MESSAGES prompt 渲染结果。
     */
    public ChatMessagesPromptRenderResult(
        SystemPromptKey promptKey,
        PromptPurpose purpose,
        PromptRenderMetadata metadata,
        List<PromptRenderedMessage> renderedMessages,
        StructuredLlmOutputContractKey structuredOutputContractKey
    ) {
        super(promptKey, purpose, PromptRenderOutputType.CHAT_MESSAGES, metadata);
        this.renderedMessages = List.copyOf(renderedMessages);
        this.structuredOutputContractKey = structuredOutputContractKey;
    }
}

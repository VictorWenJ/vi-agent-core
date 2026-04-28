package com.vi.agent.core.runtime.prompt;

import com.vi.agent.core.model.prompt.PromptRenderMetadata;
import com.vi.agent.core.model.prompt.PromptRenderOutputType;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import lombok.Getter;

import java.util.List;

/**
 * CHAT_MESSAGES prompt 渲染结果。
 */
@Getter
public final class ChatMessagesPromptRenderResult extends AbstractPromptRenderResult {

    /** 渲染后的消息列表。 */
    private final List<PromptRenderedMessage> messages;

    /** 结构化输出契约 key。 */
    private final StructuredLlmOutputContractKey structuredOutputContractKey;

    /**
     * 构造 CHAT_MESSAGES prompt 渲染结果。
     */
    public ChatMessagesPromptRenderResult(
        PromptRenderMetadata metadata,
        List<PromptRenderedMessage> messages,
        StructuredLlmOutputContractKey structuredOutputContractKey
    ) {
        super(PromptRenderOutputType.CHAT_MESSAGES, metadata);
        this.messages = List.copyOf(messages);
        this.structuredOutputContractKey = structuredOutputContractKey;
    }
}

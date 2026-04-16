package com.vi.agent.core.infra.provider.protocol.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 流式块响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionsStreamChunk {

    /**
     * 流式 choice 列表。
     */
    private List<ChatCompletionsStreamChoice> choices;
}

package com.vi.agent.core.infra.provider.protocol.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Chat Completion 响应体。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionsResponse {

    /**
     * 结果列表。
     */
    private List<ChatCompletionsChoice> choices;
}

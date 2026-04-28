package com.vi.agent.core.infra.provider.protocol.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Chat completion response.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionsResponse {

    /**
     * Provider 响应 ID。
     */
    private String id;

    private List<ChatCompletionsChoice> choices;

    private ChatCompletionsUsage usage;

    private String model;
}

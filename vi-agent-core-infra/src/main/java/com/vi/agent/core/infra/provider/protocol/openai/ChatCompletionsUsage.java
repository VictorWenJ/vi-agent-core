package com.vi.agent.core.infra.provider.protocol.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * OpenAI-compatible usage payload.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionsUsage {

    @JsonProperty("prompt_tokens")
    private Integer promptTokens;

    @JsonProperty("completion_tokens")
    private Integer completionTokens;

    @JsonProperty("total_tokens")
    private Integer totalTokens;
}

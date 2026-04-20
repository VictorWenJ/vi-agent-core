package com.vi.agent.core.infra.provider.protocol.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Stream chunk response.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionsStreamChunk {

    private List<ChatCompletionsStreamChoice> choices;

    private ChatCompletionsUsage usage;

    private String model;
}

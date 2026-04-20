package com.vi.agent.core.model.port;

import com.vi.agent.core.model.llm.ModelRequest;
import com.vi.agent.core.model.llm.ModelResponse;

import java.util.function.Consumer;

/**
 * Provider neutral LLM gateway.
 */
public interface LlmGateway {

    ModelResponse generate(ModelRequest modelRequest);

    ModelResponse generateStreaming(ModelRequest modelRequest, Consumer<String> chunkConsumer);
}

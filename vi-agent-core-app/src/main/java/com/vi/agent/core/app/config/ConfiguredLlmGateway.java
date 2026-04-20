package com.vi.agent.core.app.config;

import com.vi.agent.core.app.config.properties.ProviderRoutingProperties;
import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.model.llm.ModelRequest;
import com.vi.agent.core.model.llm.ModelResponse;
import com.vi.agent.core.model.port.LlmGateway;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Selects concrete llm gateway by configured default provider.
 */
@Primary
@Component("llmGateway")
public class ConfiguredLlmGateway implements LlmGateway {

    @Resource(name = "deepseekLlmGateway")
    private LlmGateway deepseekLlmGateway;

    @Resource(name = "doubaoLlmGateway")
    private LlmGateway doubaoLlmGateway;

    @Resource(name = "openaiLlmGateway")
    private LlmGateway openaiLlmGateway;

    @Resource
    private ProviderRoutingProperties providerRoutingProperties;

    @Override
    public ModelResponse generate(ModelRequest modelRequest) {
        return selectedGateway().generate(modelRequest);
    }

    @Override
    public ModelResponse generateStreaming(ModelRequest modelRequest, Consumer<String> chunkConsumer) {
        return selectedGateway().generateStreaming(modelRequest, chunkConsumer);
    }

    private LlmGateway selectedGateway() {
        String provider = providerRoutingProperties.getDefaultProvider() == null
            ? ""
            : providerRoutingProperties.getDefaultProvider().trim().toLowerCase();
        return switch (provider) {
            case "deepseek" -> deepseekLlmGateway;
            case "doubao" -> doubaoLlmGateway;
            case "openai" -> openaiLlmGateway;
            default -> throw new AgentRuntimeException(
                ErrorCode.PROVIDER_CONFIG_INVALID_FAILED,
                "unsupported provider: " + providerRoutingProperties.getDefaultProvider()
            );
        };
    }
}

package com.vi.agent.core.infra.provider;

import com.vi.agent.core.infra.provider.base.OpenAICompatibleChatProvider;
import com.vi.agent.core.infra.provider.config.DeepSeekProperties;
import com.vi.agent.core.infra.provider.protocol.openai.ChatCompletionsRequest;
import com.vi.agent.core.model.llm.ModelRequest;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * DeepSeek provider implementation.
 */
@Primary
@Component("deepseekLlmGateway")
public class DeepSeekChatProvider extends OpenAICompatibleChatProvider {

    @Resource
    private DeepSeekProperties properties;

    @Override
    protected String providerName() {
        return "DeepSeek";
    }

    @Override
    protected String providerKey() {
        return "deepseek";
    }

    @Override
    protected String baseUrl() {
        return properties.getBaseUrl();
    }

    @Override
    protected String chatPath() {
        return properties.getChatPath();
    }

    @Override
    protected String apiKey() {
        return properties.getApiKey();
    }

    @Override
    protected String model() {
        return properties.getModel();
    }

    @Override
    protected int connectTimeoutMs() {
        return properties.getConnectTimeoutMs();
    }

    @Override
    protected int readTimeoutMs() {
        return properties.getReadTimeoutMs();
    }

    @Override
    protected ChatCompletionsRequest buildRequest(ModelRequest modelRequest, boolean stream) {
        ChatCompletionsRequest request = super.buildRequest(modelRequest, stream);
        request.setThinkingType(properties.getThinkingType());
        request.setMaxTokens(properties.getMaxTokens());
        request.setTemperature(properties.getTemperature());
        return request;
    }
}

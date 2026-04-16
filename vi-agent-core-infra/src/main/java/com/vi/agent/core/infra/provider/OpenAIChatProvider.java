package com.vi.agent.core.infra.provider;

import com.vi.agent.core.infra.provider.base.OpenAICompatibleChatProvider;
import com.vi.agent.core.infra.provider.http.LlmHttpExecutor;
import com.vi.agent.core.infra.provider.config.OpenAIProperties;

/**
 * OpenAI Provider 实现。
 */
public class OpenAIChatProvider extends OpenAICompatibleChatProvider {

    private final OpenAIProperties properties;

    public OpenAIChatProvider(OpenAIProperties properties, LlmHttpExecutor httpExecutor) {
        super(httpExecutor);
        this.properties = properties;
    }

    @Override
    protected String providerName() {
        return "OpenAi";
    }

    @Override
    protected String providerKey() {
        return "openai";
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
}
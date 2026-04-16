package com.vi.agent.core.infra.provider;

import com.vi.agent.core.infra.provider.common.AbstractOpenAiCompatibleProvider;
import com.vi.agent.core.infra.provider.common.LlmHttpExecutor;
import com.vi.agent.core.infra.provider.config.OpenAiProperties;

/**
 * OpenAI Provider 实现。
 */
public class OpenAiProvider extends AbstractOpenAiCompatibleProvider {

    private final OpenAiProperties properties;

    public OpenAiProvider(OpenAiProperties properties, LlmHttpExecutor httpExecutor) {
        super(httpExecutor);
        this.properties = properties;
    }

    @Override
    protected String providerName() {
        return "OpenAi";
    }

    @Override
    protected String providerKey() {
        return "";
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
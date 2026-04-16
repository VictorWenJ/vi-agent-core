package com.vi.agent.core.infra.provider;

import com.vi.agent.core.infra.provider.common.AbstractOpenAiCompatibleProvider;
import com.vi.agent.core.infra.provider.common.LlmHttpExecutor;
import com.vi.agent.core.infra.provider.config.DeepSeekProperties;

/**
 * DeepSeek Provider 实现。
 */
public class DeepSeekProvider extends AbstractOpenAiCompatibleProvider {

    private final DeepSeekProperties properties;

    public DeepSeekProvider(DeepSeekProperties properties, LlmHttpExecutor httpExecutor) {
        super(httpExecutor);
        this.properties = properties;
    }

    @Override
    protected String providerName() {
        return "DeepSeek";
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
package com.vi.agent.core.infra.provider;

import com.vi.agent.core.infra.provider.config.DoubaoProperties;
import com.vi.agent.core.infra.provider.vo.AbstractOpenAiCompatibleProvider;
import com.vi.agent.core.infra.provider.vo.LlmHttpExecutor;

/**
 * Doubao Provider 实现。
 */
public class DoubaoProvider extends AbstractOpenAiCompatibleProvider {

    private final DoubaoProperties properties;

    public DoubaoProvider(DoubaoProperties properties) {
        super();
        this.properties = properties;
    }

    public DoubaoProvider(DoubaoProperties properties, LlmHttpExecutor httpExecutor) {
        super(httpExecutor);
        this.properties = properties;
    }

    @Override
    protected String providerName() {
        return "Doubao";
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
package com.vi.agent.core.infra.provider;

import com.vi.agent.core.infra.provider.base.OpenAICompatibleChatProvider;
import com.vi.agent.core.infra.provider.common.LlmHttpExecutor;
import com.vi.agent.core.infra.provider.config.DoubaoProperties;

/**
 * 豆包 Provider 实现。
 */
public class DoubaoChatProvider extends OpenAICompatibleChatProvider {

    private final DoubaoProperties properties;

    public DoubaoChatProvider(DoubaoProperties properties, LlmHttpExecutor httpExecutor) {
        super(httpExecutor);
        this.properties = properties;
    }

    @Override
    protected String providerName() {
        return "Doubao";
    }

    @Override
    protected String providerKey() {
        return "doubao";
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
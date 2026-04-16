package com.vi.agent.core.infra.provider.factory;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.infra.provider.DeepSeekChatProvider;
import com.vi.agent.core.infra.provider.DoubaoChatProvider;
import com.vi.agent.core.infra.provider.LlmProvider;
import com.vi.agent.core.infra.provider.OpenAIChatProvider;
import com.vi.agent.core.infra.provider.http.LlmHttpExecutor;
import com.vi.agent.core.infra.provider.config.DeepSeekProperties;
import com.vi.agent.core.infra.provider.config.DoubaoProperties;
import com.vi.agent.core.infra.provider.config.OpenAIProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * LLM Provider 工厂。
 */
@Slf4j
public class DefaultLlmProviderFactory {

    private final DeepSeekProperties deepSeekProperties;
    private final DoubaoProperties doubaoProperties;
    private final OpenAIProperties openAIProperties;
    private final LlmHttpExecutor httpExecutor;

    public DefaultLlmProviderFactory(DeepSeekProperties deepSeekProperties, DoubaoProperties doubaoProperties, OpenAIProperties openAIProperties, LlmHttpExecutor httpExecutor) {
        this.deepSeekProperties = deepSeekProperties;
        this.doubaoProperties = doubaoProperties;
        this.openAIProperties = openAIProperties;
        this.httpExecutor = httpExecutor;
    }

    public LlmProvider create(String providerName) {
        String normalized = providerName == null ? "" : providerName.trim().toLowerCase();

        return switch (normalized) {
            case "deepseek" -> {
                log.info("Create LlmProvider: deepseek");
                yield new DeepSeekChatProvider(deepSeekProperties, httpExecutor);
            }
            case "doubao" -> {
                log.info("Create LlmProvider: doubao");
                yield new DoubaoChatProvider(doubaoProperties, httpExecutor);
            }
            case "openai" -> {
                log.info("Create LlmProvider: openai");
                yield new OpenAIChatProvider(openAIProperties, httpExecutor);
            }
            default -> throw new AgentRuntimeException(
                ErrorCode.PROVIDER_CONFIG_INVALID_FAILED,
                "Unsupported llm provider: " + providerName
            );
        };
    }
}
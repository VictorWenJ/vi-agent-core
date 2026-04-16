package com.vi.agent.core.infra.provider;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.infra.provider.common.LlmHttpExecutor;
import com.vi.agent.core.infra.provider.config.DeepSeekProperties;
import com.vi.agent.core.infra.provider.config.DoubaoProperties;
import com.vi.agent.core.infra.provider.config.OpenAiProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * LLM Provider 工厂。
 */
@Slf4j
public class LlmProviderFactory {

    private final DeepSeekProperties deepSeekProperties;
    private final DoubaoProperties doubaoProperties;
    private final OpenAiProperties openAiProperties;
    private final LlmHttpExecutor httpExecutor;

    public LlmProviderFactory(
        DeepSeekProperties deepSeekProperties,
        DoubaoProperties doubaoProperties,
        OpenAiProperties openAiProperties,
        LlmHttpExecutor httpExecutor
    ) {
        this.deepSeekProperties = deepSeekProperties;
        this.doubaoProperties = doubaoProperties;
        this.openAiProperties = openAiProperties;
        this.httpExecutor = httpExecutor;
    }

    public LlmProvider create(String providerName) {
        String normalized = providerName == null ? "" : providerName.trim().toLowerCase();

        return switch (normalized) {
            case "deepseek" -> {
                log.info("Create LlmProvider: deepseek");
                yield new DeepSeekProvider(deepSeekProperties, httpExecutor);
            }
            case "doubao" -> {
                log.info("Create LlmProvider: doubao");
                yield new DoubaoProvider(doubaoProperties, httpExecutor);
            }
            case "openai" -> {
                log.info("Create LlmProvider: openai");
                yield new OpenAiProvider(openAiProperties, httpExecutor);
            }
            default -> throw new AgentRuntimeException(
                ErrorCode.CONFIG_INVALID,
                "Unsupported llm provider: " + providerName
            );
        };
    }
}
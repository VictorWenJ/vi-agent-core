package com.vi.agent.core.app.api.config;

import com.vi.agent.core.infra.provider.LlmProvider;
import com.vi.agent.core.infra.provider.factory.DefaultLlmProviderFactory;
import com.vi.agent.core.infra.provider.common.JdkLlmHttpExecutor;
import com.vi.agent.core.infra.provider.common.LlmHttpExecutor;
import com.vi.agent.core.infra.provider.config.DeepSeekProperties;
import com.vi.agent.core.infra.provider.config.DoubaoProperties;
import com.vi.agent.core.infra.provider.config.OpenAiProperties;
import com.vi.agent.core.app.api.config.properties.ProviderRoutingProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ProviderConfig {

    @Bean
    @ConfigurationProperties(prefix = "vi.agent.provider.deepseek")
    public DeepSeekProperties deepSeekProperties() {
        return new DeepSeekProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "vi.agent.provider.doubao")
    public DoubaoProperties doubaoProperties() {
        return new DoubaoProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "vi.agent.provider.openai")
    public OpenAiProperties openAiProperties() {
        return new OpenAiProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "vi.agent.provider.routing")
    public ProviderRoutingProperties providerRoutingProperties() {
        return new ProviderRoutingProperties();
    }

    @Bean
    public LlmHttpExecutor llmHttpExecutor() {
        return new JdkLlmHttpExecutor();
    }

    @Bean
    public DefaultLlmProviderFactory llmProviderFactory(
        DeepSeekProperties deepSeekProperties,
        DoubaoProperties doubaoProperties,
        OpenAiProperties openAiProperties,
        LlmHttpExecutor llmHttpExecutor
    ) {
        return new DefaultLlmProviderFactory(
            deepSeekProperties,
            doubaoProperties,
            openAiProperties,
            llmHttpExecutor
        );
    }

    @Bean
    public LlmProvider llmProvider(
        DefaultLlmProviderFactory defaultLlmProviderFactory,
        ProviderRoutingProperties providerRoutingProperties
    ) {
        return defaultLlmProviderFactory.create(providerRoutingProperties.getDefaultProvider());
    }
}
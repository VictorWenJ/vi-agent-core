package com.vi.agent.core.app.config;

import com.vi.agent.core.infra.provider.LlmProvider;
import com.vi.agent.core.infra.provider.factory.DefaultLlmProviderFactory;
import com.vi.agent.core.infra.provider.http.JdkLlmHttpExecutor;
import com.vi.agent.core.infra.provider.http.LlmHttpExecutor;
import com.vi.agent.core.infra.provider.config.DeepSeekProperties;
import com.vi.agent.core.infra.provider.config.DoubaoProperties;
import com.vi.agent.core.infra.provider.config.OpenAIProperties;
import com.vi.agent.core.app.config.properties.ProviderRoutingProperties;
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
    public OpenAIProperties openAIProperties() {
        return new OpenAIProperties();
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
        OpenAIProperties openAIProperties,
        LlmHttpExecutor llmHttpExecutor
    ) {
        return new DefaultLlmProviderFactory(
            deepSeekProperties,
            doubaoProperties,
            openAIProperties,
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
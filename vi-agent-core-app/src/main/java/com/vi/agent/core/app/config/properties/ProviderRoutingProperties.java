package com.vi.agent.core.app.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Provider 路由配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "vi.agent.provider.routing")
public class ProviderRoutingProperties {

    /**
     * 默认 Provider 名称。
     * 可选值：deepseek / doubao / openai
     */
    private String defaultProvider = "deepseek";
}

package com.vi.agent.core.app.config.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * Provider 路由配置。
 */
@Getter
@Setter
public class ProviderRoutingProperties {

    /**
     * 默认 Provider 名称。
     * 可选值：deepseek / doubao / openai
     */
    private String defaultProvider = "deepseek";
}
package com.vi.agent.core.infra.provider.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek 配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "vi.agent.provider.deepseek")
public class DeepSeekProperties {

    /**
     * API Key。
     */
    private String apiKey = "";

    /**
     * 模型名称。
     */
    private String model = "deepseek-chat";

    /**
     * Base URL。
     */
    private String baseUrl = "https://api.deepseek.com";

    /**
     * Chat API 路径。
     */
    private String chatPath = "/chat/completions";

    /**
     * 连接超时，单位毫秒。
     */
    private int connectTimeoutMs = 3000;

    /**
     * 读取超时，单位毫秒。
     */
    private int readTimeoutMs = 60000;
}

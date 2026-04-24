package com.vi.agent.core.infra.provider.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DeepSeek config.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "vi.agent.provider.deepseek")
public class DeepSeekProperties {

    /**
     * API Key.
     */
    private String apiKey = "";

    /**
     * Model name.
     */
    private String model = "deepseek-v4-flash";

    /**
     * Base URL.
     */
    private String baseUrl = "https://api.deepseek.com";

    /**
     * Chat API path.
     */
    private String chatPath = "/chat/completions";

    /**
     * Connect timeout milliseconds.
     */
    private int connectTimeoutMs = 3000;

    /**
     * Read timeout milliseconds.
     */
    private int readTimeoutMs = 60000;

    /**
     * Thinking type passed to DeepSeek API.
     */
    private String thinkingType = "disabled";

    /**
     * Max tokens passed to provider request.
     */
    private Integer maxTokens = 512;

    /**
     * Temperature passed to provider request.
     */
    private Double temperature = 0.2d;
}

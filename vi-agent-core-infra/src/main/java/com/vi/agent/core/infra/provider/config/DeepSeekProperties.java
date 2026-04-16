package com.vi.agent.core.infra.provider.config;

import lombok.Getter;
import lombok.Setter;

/**
 * DeepSeek 配置。
 */
@Getter
@Setter
public class DeepSeekProperties {

    /** 接口基础地址。 */
    private String baseUrl = "https://api.deepseek.com";

    /** Chat completions 路径。 */
    private String chatPath = "/chat/completions";

    /** API Key。 */
    private String apiKey;

    /** 默认模型。 */
    private String model = "deepseek-chat";

    /** 连接超时毫秒。 */
    private int connectTimeoutMs = 5000;

    /** 读取超时毫秒。 */
    private int readTimeoutMs = 30000;
}

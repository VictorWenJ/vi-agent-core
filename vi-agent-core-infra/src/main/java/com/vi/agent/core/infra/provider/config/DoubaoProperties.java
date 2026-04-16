package com.vi.agent.core.infra.provider.config;

import lombok.Getter;
import lombok.Setter;

/**
 * 豆包 Provider 配置。
 */
@Getter
@Setter
public class DoubaoProperties {

    /**
     * 火山方舟 API Key。
     */
    private String apiKey;

    /**
     * 模型名称，例如 doubao-seed-1-6-250615。
     */
    private String model;

    /**
     * 基础地址。
     */
    private String baseUrl = "https://ark.cn-beijing.volces.com";

    /**
     * Chat API 路径。
     */
    private String chatPath = "/api/v3/chat/completions";

    /**
     * 连接超时时间，单位毫秒。
     */
    private int connectTimeoutMs = 3000;

    /**
     * 读取超时时间，单位毫秒。
     */
    private int readTimeoutMs = 60000;
}
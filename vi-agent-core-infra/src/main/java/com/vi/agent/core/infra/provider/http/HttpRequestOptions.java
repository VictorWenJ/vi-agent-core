package com.vi.agent.core.infra.provider.http;

import lombok.Builder;
import lombok.Getter;

/**
 * HTTP 请求选项。
 */
@Getter
@Builder
public class HttpRequestOptions {

    /**
     * 连接超时，单位毫秒。
     */
    private final int connectTimeoutMs;

    /**
     * 读取超时，单位毫秒。
     */
    private final int readTimeoutMs;
}

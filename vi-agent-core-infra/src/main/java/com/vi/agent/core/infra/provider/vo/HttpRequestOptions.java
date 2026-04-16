package com.vi.agent.core.infra.provider.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * HTTP 请求选项。
 */
@Getter
@Builder
public class HttpRequestOptions {

    /**
     * 连接超时时间，单位毫秒。
     */
    private final int connectTimeoutMs;

    /**
     * 读取超时时间，单位毫秒。
     */
    private final int readTimeoutMs;
}
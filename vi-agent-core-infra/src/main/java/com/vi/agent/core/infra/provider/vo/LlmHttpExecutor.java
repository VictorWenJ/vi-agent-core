package com.vi.agent.core.infra.provider.vo;

import java.util.Map;
import java.util.function.Consumer;

/**
 * LLM HTTP 执行器抽象。
 */
public interface LlmHttpExecutor {

    /**
     * 同步 POST。
     *
     * @param url 请求地址
     * @param headers 请求头
     * @param body 请求体
     * @param options 请求选项
     * @return 响应体字符串
     * @throws Exception 执行异常
     */
    String post(
        String url,
        Map<String, String> headers,
        String body,
        HttpRequestOptions options
    ) throws Exception;

    /**
     * 流式 POST。
     *
     * @param url 请求地址
     * @param headers 请求头
     * @param body 请求体
     * @param options 请求选项
     * @param lineConsumer 每一行流式数据回调
     * @throws Exception 执行异常
     */
    void postStream(
        String url,
        Map<String, String> headers,
        String body,
        HttpRequestOptions options,
        Consumer<String> lineConsumer
    ) throws Exception;
}
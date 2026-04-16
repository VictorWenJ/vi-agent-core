package com.vi.agent.core.infra.provider.http;

import java.util.Map;
import java.util.function.Consumer;

/**
 * LLM HTTP 执行器。
 */
public interface LlmHttpExecutor {

    /**
     * 同步 POST 请求。
     *
     * @param url     请求地址
     * @param headers 请求头
     * @param body    请求体
     * @param options 请求选项
     * @return 响应体
     * @throws Exception 执行异常
     */
    String post(String url, Map<String, String> headers, String body, HttpRequestOptions options) throws Exception;

    /**
     * 流式 POST 请求。
     *
     * @param url          请求地址
     * @param headers      请求头
     * @param body         请求体
     * @param options      请求选项
     * @param lineConsumer 行消费器
     * @throws Exception 执行异常
     */
    void postStream(String url, Map<String, String> headers, String body, HttpRequestOptions options, Consumer<String> lineConsumer) throws Exception;
}

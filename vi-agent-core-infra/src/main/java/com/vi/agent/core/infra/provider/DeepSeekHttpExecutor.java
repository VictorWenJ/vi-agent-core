package com.vi.agent.core.infra.provider;

import java.util.function.Consumer;

interface DeepSeekHttpExecutor {
    String post(String url, String apiKey, String body, int timeoutMs) throws Exception;

    void postStream(String url, String apiKey, String body, int timeoutMs, Consumer<String> lineConsumer) throws Exception;
}

package com.vi.agent.core.infra.provider.vo;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 基于 JDK HttpClient 的公共执行器。
 */
@Slf4j
public class JdkLlmHttpExecutor implements LlmHttpExecutor {

    /**
     * 按 connectTimeout 复用 HttpClient，避免每次 new。
     */
    private final ConcurrentHashMap<Integer, HttpClient> clientCache = new ConcurrentHashMap<>();

    @Override
    public String post(String url, Map<String, String> headers, String body, HttpRequestOptions options) throws Exception {
        HttpClient httpClient = getOrCreateClient(options);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMillis(Math.max(1000, options.getReadTimeoutMs())))
            .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8));

        applyHeaders(builder, headers);

        HttpRequest request = builder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AgentRuntimeException(
                ErrorCode.PROVIDER_CALL_FAILED,
                "HTTP status=" + response.statusCode() + ", body=" + response.body()
            );
        }

        return response.body();
    }

    @Override
    public void postStream(String url, Map<String, String> headers, String body, HttpRequestOptions options, Consumer<String> lineConsumer) throws Exception {
        HttpClient httpClient = getOrCreateClient(options);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofMillis(Math.max(1000, options.getReadTimeoutMs())))
            .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8));

        applyHeaders(builder, headers);

        HttpRequest request = builder.build();
        HttpResponse<InputStream> response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofInputStream()
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String bodyText;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                bodyText = reader.lines().reduce("", (a, b) -> a + b);
            }
            throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, "HTTP stream status=" + response.statusCode() + ", body=" + bodyText);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (lineConsumer != null) {
                    lineConsumer.accept(line);
                }
            }
        }
    }

    private HttpClient getOrCreateClient(HttpRequestOptions options) {
        int connectTimeoutMs = Math.max(1000, options.getConnectTimeoutMs());
        return clientCache.computeIfAbsent(connectTimeoutMs, timeout ->
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout))
                .build()
        );
    }

    private void applyHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }
    }
}
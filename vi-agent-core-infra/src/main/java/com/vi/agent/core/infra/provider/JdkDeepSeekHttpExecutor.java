package com.vi.agent.core.infra.provider;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.infra.provider.config.DeepSeekProperties;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

class JdkDeepSeekHttpExecutor implements DeepSeekHttpExecutor {

    private final HttpClient httpClient;

    JdkDeepSeekHttpExecutor(DeepSeekProperties properties) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(Math.max(1000, properties.getConnectTimeoutMs())))
            .build();
    }

    @Override
    public String post(String url, String apiKey, String body, int timeoutMs) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .timeout(Duration.ofMillis(Math.max(1000, timeoutMs)))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AgentRuntimeException(
                ErrorCode.PROVIDER_CALL_FAILED,
                "DeepSeek HTTP status=" + response.statusCode() + ", body=" + response.body()
            );
        }
        return response.body();
    }

    @Override
    public void postStream(String url, String apiKey, String body, int timeoutMs, Consumer<String> lineConsumer) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .timeout(Duration.ofMillis(Math.max(1000, timeoutMs)))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String bodyText;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                bodyText = reader.lines().reduce("", (a, b) -> a + b);
            }
            throw new AgentRuntimeException(
                ErrorCode.PROVIDER_CALL_FAILED,
                "DeepSeek stream HTTP status=" + response.statusCode() + ", body=" + bodyText
            );
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
}

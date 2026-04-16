package com.vi.agent.core.infra.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.common.util.ValidationUtils;
import com.vi.agent.core.infra.provider.config.DeepSeekProperties;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.ToolExecutionMessage;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolDefinition;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * DeepSeek Provider 实现。
 */
@Slf4j
public class DeepSeekProvider implements LlmProvider {

    private final DeepSeekProperties properties;
    private final DeepSeekHttpExecutor httpExecutor;

    public DeepSeekProvider(DeepSeekProperties properties) {
        this(properties, new JdkDeepSeekHttpExecutor(properties));
    }

    DeepSeekProvider(DeepSeekProperties properties, DeepSeekHttpExecutor httpExecutor) {
        this.properties = properties;
        this.httpExecutor = httpExecutor;
    }

    @Override
    public AssistantMessage generate(AgentRunContext runContext) {
        assertConfigured();
        ApiChatRequest request = buildRequest(runContext, false);
        String payload = JsonUtils.toJson(request);
        try {
            log.info("DeepSeek generate start runId={} sessionId={} iteration={}",
                runContext.getRunId(), runContext.getSessionId(), runContext.getIteration());
            String responseBody = httpExecutor.post(endpoint(), properties.getApiKey(), payload, properties.getReadTimeoutMs());
            return parseAssistantMessage(responseBody, runContext.getTurnId());
        } catch (AgentRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, "DeepSeek 同步调用失败", e);
        }
    }

    @Override
    public AssistantMessage generateStreaming(AgentRunContext runContext, Consumer<String> chunkConsumer) {
        assertConfigured();
        ApiChatRequest request = buildRequest(runContext, true);
        String payload = JsonUtils.toJson(request);

        StringBuilder fullContent = new StringBuilder();
        Map<String, ToolCall> toolCallMap = new LinkedHashMap<>();

        try {
            log.info("DeepSeek stream start runId={} sessionId={} iteration={}",
                runContext.getRunId(), runContext.getSessionId(), runContext.getIteration());
            httpExecutor.postStream(endpoint(), properties.getApiKey(), payload, properties.getReadTimeoutMs(), line -> {
                if (line == null || line.isBlank() || !line.startsWith("data:")) {
                    return;
                }
                String data = line.substring("data:".length()).trim();
                if ("[DONE]".equals(data)) {
                    return;
                }
                ApiStreamResponse streamResponse = JsonUtils.jsonToBean(data, ApiStreamResponse.class);
                if (streamResponse == null || streamResponse.getChoices() == null) {
                    return;
                }
                for (ApiStreamChoice choice : streamResponse.getChoices()) {
                    ApiDelta delta = choice.getDelta();
                    if (delta == null) {
                        continue;
                    }
                    if (delta.getContent() != null && !delta.getContent().isBlank()) {
                        fullContent.append(delta.getContent());
                        if (chunkConsumer != null) {
                            chunkConsumer.accept(delta.getContent());
                        }
                    }
                    collectToolCalls(toolCallMap, delta.getToolCalls(), runContext.getTurnId());
                }
            });
            return new AssistantMessage(fullContent.toString(), new ArrayList<>(toolCallMap.values()));
        } catch (AgentRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, "DeepSeek 流式调用失败", e);
        }
    }

    private ApiChatRequest buildRequest(AgentRunContext runContext, boolean stream) {
        ApiChatRequest request = new ApiChatRequest();
        request.setModel(properties.getModel());
        request.setStream(stream);

        List<ApiMessage> apiMessages = new ArrayList<>();
        for (Message message : runContext.getWorkingMessages()) {
            ApiMessage apiMessage = toApiMessage(message);
            if (apiMessage != null) {
                apiMessages.add(apiMessage);
            }
        }
        request.setMessages(apiMessages);

        List<ToolDefinition> availableTools = runContext.getAvailableTools();
        if (availableTools != null && !availableTools.isEmpty()) {
            List<ApiToolDefinition> tools = new ArrayList<>();
            for (ToolDefinition definition : availableTools) {
                ApiToolDefinition toolDefinition = toApiToolDefinition(definition);
                if (toolDefinition != null) {
                    tools.add(toolDefinition);
                }
            }
            if (!tools.isEmpty()) {
                request.setTools(tools);
                request.setToolChoice("auto");
            }
        }
        return request;
    }

    private ApiMessage toApiMessage(Message message) {
        if (message == null) {
            return null;
        }
        ApiMessage apiMessage = new ApiMessage();
        apiMessage.setRole(message.getRole());
        apiMessage.setContent(message.getContent());

        if (message instanceof AssistantMessage assistantMessage && !assistantMessage.getToolCalls().isEmpty()) {
            List<ApiToolCall> apiToolCalls = new ArrayList<>();
            for (ToolCall toolCall : assistantMessage.getToolCalls()) {
                apiToolCalls.add(toApiToolCall(toolCall));
            }
            apiMessage.setToolCalls(apiToolCalls);
        }
        if (message instanceof ToolExecutionMessage toolExecutionMessage) {
            apiMessage.setToolCallId(toolExecutionMessage.getToolCallId());
            apiMessage.setName(toolExecutionMessage.getToolName());
            apiMessage.setContent(toolExecutionMessage.getToolOutput());
        }
        return apiMessage;
    }

    private ApiToolDefinition toApiToolDefinition(ToolDefinition definition) {
        if (definition == null || definition.getName() == null || definition.getName().isBlank()) {
            return null;
        }
        ApiToolDefinition apiToolDefinition = new ApiToolDefinition();
        apiToolDefinition.setType("function");

        ApiFunction function = new ApiFunction();
        function.setName(definition.getName());
        function.setDescription(Optional.ofNullable(definition.getDescription()).orElse(""));

        Object parameters = JsonUtils.jsonToBean(
            Optional.ofNullable(definition.getParametersJson()).orElse("{}"),
            Object.class
        );
        function.setParameters(parameters == null ? Map.of("type", "object", "properties", Map.of()) : parameters);

        apiToolDefinition.setFunction(function);
        return apiToolDefinition;
    }

    private ApiToolCall toApiToolCall(ToolCall toolCall) {
        ApiToolCall apiToolCall = new ApiToolCall();
        apiToolCall.setId(toolCall.getToolCallId());
        apiToolCall.setType("function");
        ApiFunction function = new ApiFunction();
        function.setName(toolCall.getToolName());
        function.setArguments(Optional.ofNullable(toolCall.getArgumentsJson()).orElse("{}"));
        apiToolCall.setFunction(function);
        return apiToolCall;
    }

    private AssistantMessage parseAssistantMessage(String body, String defaultTurnId) {
        ApiChatResponse response = JsonUtils.jsonToBean(body, ApiChatResponse.class);
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, "DeepSeek 返回为空");
        }

        ApiMessage message = response.getChoices().get(0).getMessage();
        if (message == null) {
            throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, "DeepSeek 返回缺少 message");
        }

        List<ToolCall> toolCalls = new ArrayList<>();
        Map<String, ToolCall> toolCallMap = new LinkedHashMap<>();
        collectToolCalls(toolCallMap, message.getToolCalls(), defaultTurnId);
        toolCalls.addAll(toolCallMap.values());

        return new AssistantMessage(Optional.ofNullable(message.getContent()).orElse(""), toolCalls);
    }

    private void collectToolCalls(Map<String, ToolCall> toolCallMap, List<ApiToolCall> apiToolCalls, String defaultTurnId) {
        if (apiToolCalls == null || apiToolCalls.isEmpty()) {
            return;
        }
        for (ApiToolCall apiToolCall : apiToolCalls) {
            if (apiToolCall == null || apiToolCall.getFunction() == null) {
                continue;
            }
            String toolName = apiToolCall.getFunction().getName();
            if (toolName == null || toolName.isBlank()) {
                continue;
            }
            String toolCallId = apiToolCall.getId();
            if (toolCallId == null || toolCallId.isBlank()) {
                toolCallId = "tc-ds-" + toolCallMap.size();
            }
            ToolCall toolCall = ToolCall.builder()
                .toolCallId(toolCallId)
                .toolName(toolName)
                .argumentsJson(Optional.ofNullable(apiToolCall.getFunction().getArguments()).orElse("{}"))
                .turnId(defaultTurnId)
                .build();
            toolCallMap.put(toolCallId, toolCall);
        }
    }

    private String endpoint() {
        String base = Optional.ofNullable(properties.getBaseUrl()).orElse("").trim();
        String path = Optional.ofNullable(properties.getChatPath()).orElse("/chat/completions").trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path;
    }

    private void assertConfigured() {
        ValidationUtils.requireNonBlank(properties.getBaseUrl(), "deepseek.baseUrl");
        ValidationUtils.requireNonBlank(properties.getApiKey(), "deepseek.apiKey");
        ValidationUtils.requireNonBlank(properties.getModel(), "deepseek.model");
    }

    interface DeepSeekHttpExecutor {
        String post(String url, String apiKey, String body, int timeoutMs) throws Exception;

        void postStream(String url, String apiKey, String body, int timeoutMs, Consumer<String> lineConsumer) throws Exception;
    }

    private static class JdkDeepSeekHttpExecutor implements DeepSeekHttpExecutor {

        private final HttpClient httpClient;

        private JdkDeepSeekHttpExecutor(DeepSeekProperties properties) {
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

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ApiChatRequest {

        /** 模型名称。 */
        private String model;

        /** 输入消息列表。 */
        private List<ApiMessage> messages;

        /** 工具定义列表。 */
        private List<ApiToolDefinition> tools;

        /** 工具选择策略。 */
        @JsonProperty("tool_choice")
        private String toolChoice;

        /** 是否流式。 */
        private boolean stream;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static class ApiChatResponse {

        /** 候选列表。 */
        private List<ApiChoice> choices;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static class ApiStreamResponse {

        /** 候选列表。 */
        private List<ApiStreamChoice> choices;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static class ApiChoice {

        /** 消息内容。 */
        private ApiMessage message;

        /** 结束原因。 */
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static class ApiStreamChoice {

        /** 增量消息。 */
        private ApiDelta delta;

        /** 结束原因。 */
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static class ApiDelta {

        /** 增量文本。 */
        private String content;

        /** 增量工具调用。 */
        @JsonProperty("tool_calls")
        private List<ApiToolCall> toolCalls;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static class ApiMessage {

        /** 角色。 */
        private String role;

        /** 文本内容。 */
        private String content;

        /** tool call id。 */
        @JsonProperty("tool_call_id")
        private String toolCallId;

        /** 工具名称。 */
        private String name;

        /** 工具调用。 */
        @JsonProperty("tool_calls")
        private List<ApiToolCall> toolCalls;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static class ApiToolDefinition {

        /** 工具类型。 */
        private String type;

        /** 函数定义。 */
        private ApiFunction function;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static class ApiToolCall {

        /** 工具调用 ID。 */
        private String id;

        /** 调用类型。 */
        private String type;

        /** 函数信息。 */
        private ApiFunction function;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static class ApiFunction {

        /** 函数名称。 */
        private String name;

        /** 函数描述。 */
        private String description;

        /** 参数 schema。 */
        private Object parameters;

        /** 参数 JSON 字符串。 */
        private String arguments;
    }
}

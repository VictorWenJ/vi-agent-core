package com.vi.agent.core.infra.provider.vo;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.common.util.ValidationUtils;
import com.vi.agent.core.infra.provider.LlmProvider;
import com.vi.agent.core.infra.provider.model.*;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.ToolExecutionMessage;
import com.vi.agent.core.model.runtime.AgentRunContext;
import com.vi.agent.core.model.tool.ToolCall;
import com.vi.agent.core.model.tool.ToolDefinition;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * OpenAI 协议兼容 Provider 的公共基类。
 */
@Slf4j
public abstract class AbstractOpenAiCompatibleProvider implements LlmProvider {

    protected final LlmHttpExecutor httpExecutor;

    protected AbstractOpenAiCompatibleProvider() {
        this(new JdkLlmHttpExecutor());
    }

    protected AbstractOpenAiCompatibleProvider(LlmHttpExecutor httpExecutor) {
        this.httpExecutor = httpExecutor;
    }

    @Override
    public AssistantMessage generate(AgentRunContext runContext) {
        assertConfigured();

        ApiChatRequest request = buildRequest(runContext, false);
        String payload = JsonUtils.toJson(request);

        try {
            log.info("{} sync request start runId={} sessionId={} iteration={}",
                providerName(), runContext.getRunId(), runContext.getSessionId(), runContext.getIteration());

            String responseBody = httpExecutor.post(
                endpoint(),
                defaultHeaders(),
                payload,
                requestOptions()
            );
            return parseAssistantMessage(responseBody, runContext.getTurnId());
        } catch (AgentRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentRuntimeException(
                ErrorCode.PROVIDER_CALL_FAILED,
                providerName() + " 同步调用失败",
                e
            );
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
            log.info("{} stream request start runId={} sessionId={} iteration={}",
                providerName(), runContext.getRunId(), runContext.getSessionId(), runContext.getIteration());

            httpExecutor.postStream(
                endpoint(),
                defaultHeaders(),
                payload,
                requestOptions(),
                line -> handleStreamLine(line, runContext.getTurnId(), fullContent, toolCallMap, chunkConsumer)
            );

            return new AssistantMessage(fullContent.toString(), new ArrayList<>(toolCallMap.values()));
        } catch (AgentRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentRuntimeException(
                ErrorCode.PROVIDER_CALL_FAILED,
                providerName() + " 流式调用失败",
                e
            );
        }
    }

    protected void handleStreamLine(
        String line,
        String defaultTurnId,
        StringBuilder fullContent,
        Map<String, ToolCall> toolCallMap,
        Consumer<String> chunkConsumer
    ) {
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

            collectToolCalls(toolCallMap, delta.getToolCalls(), defaultTurnId);
        }
    }

    protected ApiChatRequest buildRequest(AgentRunContext runContext, boolean stream) {
        ApiChatRequest request = new ApiChatRequest();
        request.setModel(model());
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

    protected ApiMessage toApiMessage(Message message) {
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

    protected ApiToolDefinition toApiToolDefinition(ToolDefinition definition) {
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

    protected ApiToolCall toApiToolCall(ToolCall toolCall) {
        ApiToolCall apiToolCall = new ApiToolCall();
        apiToolCall.setId(toolCall.getToolCallId());
        apiToolCall.setType("function");

        ApiFunction function = new ApiFunction();
        function.setName(toolCall.getToolName());
        function.setArguments(Optional.ofNullable(toolCall.getArgumentsJson()).orElse("{}"));
        apiToolCall.setFunction(function);

        return apiToolCall;
    }

    protected AssistantMessage parseAssistantMessage(String body, String defaultTurnId) {
        ApiChatResponse response = JsonUtils.jsonToBean(body, ApiChatResponse.class);
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, providerName() + " 返回为空");
        }

        ApiMessage message = response.getChoices().get(0).getMessage();
        if (message == null) {
            throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, providerName() + " 返回缺少 message");
        }

        Map<String, ToolCall> toolCallMap = new LinkedHashMap<>();
        collectToolCalls(toolCallMap, message.getToolCalls(), defaultTurnId);

        return new AssistantMessage(
            Optional.ofNullable(message.getContent()).orElse(""),
            new ArrayList<>(toolCallMap.values())
        );
    }

    protected void collectToolCalls(
        Map<String, ToolCall> toolCallMap,
        List<ApiToolCall> apiToolCalls,
        String defaultTurnId
    ) {
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
                toolCallId = "tc-" + providerName().toLowerCase() + "-" + toolCallMap.size();
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

    protected String endpoint() {
        String base = Optional.ofNullable(baseUrl()).orElse("").trim();
        String path = Optional.ofNullable(chatPath()).orElse("/api/v3/chat/completions").trim();

        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        return base + path;
    }

    protected Map<String, String> defaultHeaders() {
        return Map.of(
            "Content-Type", "application/json",
            "Authorization", "Bearer " + apiKey()
        );
    }

    protected HttpRequestOptions requestOptions() {
        return HttpRequestOptions.builder()
            .connectTimeoutMs(connectTimeoutMs())
            .readTimeoutMs(readTimeoutMs())
            .build();
    }

    protected void assertConfigured() {
        ValidationUtils.requireNonBlank(baseUrl(), providerName() + ".baseUrl");
        ValidationUtils.requireNonBlank(apiKey(), providerName() + ".apiKey");
        ValidationUtils.requireNonBlank(model(), providerName() + ".model");
    }

    protected abstract String providerName();

    protected abstract String baseUrl();

    protected abstract String chatPath();

    protected abstract String apiKey();

    protected abstract String model();

    protected abstract int connectTimeoutMs();

    protected abstract int readTimeoutMs();
}
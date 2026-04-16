package com.vi.agent.core.infra.provider.base;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.common.util.ValidationUtils;
import com.vi.agent.core.infra.provider.LlmProvider;
import com.vi.agent.core.infra.provider.http.HttpRequestOptions;
import com.vi.agent.core.infra.provider.http.JdkLlmHttpExecutor;
import com.vi.agent.core.infra.provider.http.LlmHttpExecutor;
import com.vi.agent.core.infra.provider.protocol.openai.*;
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
 * OpenAI 协议兼容 Provider 公共基类。
 */
@Slf4j
public abstract class OpenAICompatibleChatProvider implements LlmProvider {

    /**
     * 公共 HTTP 执行器。
     */
    protected final LlmHttpExecutor httpExecutor;

    protected OpenAICompatibleChatProvider() {
        this(new JdkLlmHttpExecutor());
    }

    protected OpenAICompatibleChatProvider(LlmHttpExecutor httpExecutor) {
        this.httpExecutor = httpExecutor;
    }

    @Override
    public AssistantMessage generate(AgentRunContext runContext) {
        assertConfigured();
        ChatCompletionsRequest request = buildRequest(runContext, false);
        String payload = JsonUtils.toJson(request);

        try {
            log.info("{} sync request start runId={} sessionId={} iteration={}",
                providerName(), runContext.getRunId(), runContext.getSessionId(), runContext.getIteration());
            String responseBody = httpExecutor.post(endpoint(), defaultHeaders(), payload, requestOptions());
            return parseAssistantMessage(responseBody, runContext.getTurnId());
        } catch (AgentRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, providerName() + " 同步调用失败", e);
        }
    }

    @Override
    public AssistantMessage generateStreaming(AgentRunContext runContext, Consumer<String> chunkConsumer) {
        assertConfigured();
        ChatCompletionsRequest request = buildRequest(runContext, true);
        String payload = JsonUtils.toJson(request);

        StringBuilder fullContent = new StringBuilder();
        Map<String, StreamingToolCallAccumulator> toolCallStateMap = new LinkedHashMap<>();

        try {
            log.info("{} stream request start runId={} sessionId={} iteration={}",
                providerName(), runContext.getRunId(), runContext.getSessionId(), runContext.getIteration());
            httpExecutor.postStream(
                endpoint(),
                defaultHeaders(),
                payload,
                requestOptions(),
                line -> handleStreamLine(line, runContext.getTurnId(), fullContent, toolCallStateMap, chunkConsumer)
            );
            return new AssistantMessage(fullContent.toString(), toToolCalls(toolCallStateMap, runContext.getTurnId()));
        } catch (AgentRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, providerName() + " 流式调用失败", e);
        }
    }

    protected ChatCompletionsRequest buildRequest(AgentRunContext runContext, boolean stream) {
        ChatCompletionsRequest request = new ChatCompletionsRequest();
        request.setModel(model());
        request.setStream(stream);

        List<ChatCompletionsMessage> chatCompletionsMessages = new ArrayList<>();
        List<Message> workingMessages = runContext.getWorkingMessages();
        if (workingMessages != null && !workingMessages.isEmpty()) {
            for (Message message : workingMessages) {
                ChatCompletionsMessage chatCompletionsMessage = toApiMessage(message);
                if (chatCompletionsMessage != null) {
                    chatCompletionsMessages.add(chatCompletionsMessage);
                }
            }
        }
        request.setMessages(chatCompletionsMessages);

        List<ToolDefinition> availableTools = runContext.getAvailableTools();
        if (availableTools != null && !availableTools.isEmpty()) {
            List<ChatCompletionsToolDefinition> apiTools = new ArrayList<>();
            for (ToolDefinition definition : availableTools) {
                ChatCompletionsToolDefinition chatCompletionsToolDefinition = toApiToolDefinition(definition);
                if (chatCompletionsToolDefinition != null) {
                    apiTools.add(chatCompletionsToolDefinition);
                }
            }
            if (!apiTools.isEmpty()) {
                request.setTools(apiTools);
                request.setToolChoice("auto");
            }
        }
        return request;
    }

    protected ChatCompletionsMessage toApiMessage(Message message) {
        if (message == null) {
            return null;
        }
        ChatCompletionsMessage chatCompletionsMessage = new ChatCompletionsMessage();
        chatCompletionsMessage.setRole(message.getRole());
        chatCompletionsMessage.setContent(message.getContent());

        if (message instanceof AssistantMessage assistantMessage
            && assistantMessage.getToolCalls() != null
            && !assistantMessage.getToolCalls().isEmpty()) {
            List<ChatCompletionsToolCall> chatCompletionsToolCalls = new ArrayList<>();
            for (ToolCall toolCall : assistantMessage.getToolCalls()) {
                chatCompletionsToolCalls.add(toApiToolCall(toolCall));
            }
            chatCompletionsMessage.setToolCalls(chatCompletionsToolCalls);
        }

        if (message instanceof ToolExecutionMessage toolExecutionMessage) {
            chatCompletionsMessage.setToolCallId(toolExecutionMessage.getToolCallId());
            chatCompletionsMessage.setName(toolExecutionMessage.getToolName());
            chatCompletionsMessage.setContent(toolExecutionMessage.getToolOutput());
        }
        return chatCompletionsMessage;
    }

    protected ChatCompletionsToolDefinition toApiToolDefinition(ToolDefinition definition) {
        if (definition == null || definition.getName() == null || definition.getName().isBlank()) {
            return null;
        }

        ChatCompletionsToolDefinition chatCompletionsToolDefinition = new ChatCompletionsToolDefinition();
        chatCompletionsToolDefinition.setType("function");

        ChatCompletionsFunction function = new ChatCompletionsFunction();
        function.setName(definition.getName());
        function.setDescription(Optional.ofNullable(definition.getDescription()).orElse(""));

        Object parameters = JsonUtils.jsonToBean(
            Optional.ofNullable(definition.getParametersJson()).orElse("{}"),
            Object.class
        );
        function.setParameters(parameters == null ? Map.of("type", "object", "properties", Map.of()) : parameters);
        chatCompletionsToolDefinition.setFunction(function);
        return chatCompletionsToolDefinition;
    }

    protected ChatCompletionsToolCall toApiToolCall(ToolCall toolCall) {
        ChatCompletionsToolCall chatCompletionsToolCall = new ChatCompletionsToolCall();
        chatCompletionsToolCall.setId(toolCall.getToolCallId());
        chatCompletionsToolCall.setType("function");

        ChatCompletionsFunction function = new ChatCompletionsFunction();
        function.setName(toolCall.getToolName());
        function.setArguments(Optional.ofNullable(toolCall.getArgumentsJson()).orElse("{}"));
        chatCompletionsToolCall.setFunction(function);
        return chatCompletionsToolCall;
    }

    protected AssistantMessage parseAssistantMessage(String body, String defaultTurnId) {
        ChatCompletionsResponse response = JsonUtils.jsonToBean(body, ChatCompletionsResponse.class);
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, providerName() + " 返回为空");
        }

        ChatCompletionsChoice choice = response.getChoices().get(0);
        if (choice == null || choice.getMessage() == null) {
            throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, providerName() + " 返回缺少 message");
        }

        ChatCompletionsMessage message = choice.getMessage();
        List<ToolCall> toolCalls = collectToolCalls(message.getToolCalls(), defaultTurnId);
        return new AssistantMessage(Optional.ofNullable(message.getContent()).orElse(""), toolCalls);
    }

    protected void handleStreamLine(
        String line,
        String defaultTurnId,
        StringBuilder fullContent,
        Map<String, StreamingToolCallAccumulator> toolCallStateMap,
        Consumer<String> chunkConsumer
    ) {
        if (line == null || line.isBlank() || !line.startsWith("data:")) {
            return;
        }

        String data = line.substring("data:".length()).trim();
        if ("[DONE]".equals(data)) {
            return;
        }

        ChatCompletionsStreamChunk streamResponse = JsonUtils.jsonToBean(data, ChatCompletionsStreamChunk.class);
        if (streamResponse == null || streamResponse.getChoices() == null || streamResponse.getChoices().isEmpty()) {
            return;
        }

        for (ChatCompletionsStreamChoice choice : streamResponse.getChoices()) {
            ChatCompletionsDelta delta = choice.getDelta();
            if (delta == null) {
                continue;
            }
            if (delta.getContent() != null && !delta.getContent().isBlank()) {
                fullContent.append(delta.getContent());
                if (chunkConsumer != null) {
                    chunkConsumer.accept(delta.getContent());
                }
            }
            mergeStreamToolCalls(toolCallStateMap, delta.getToolCalls(), defaultTurnId);
        }
    }

    protected List<ToolCall> collectToolCalls(List<ChatCompletionsToolCall> chatCompletionsToolCalls, String defaultTurnId) {
        if (chatCompletionsToolCalls == null || chatCompletionsToolCalls.isEmpty()) {
            return List.of();
        }
        List<ToolCall> toolCalls = new ArrayList<>();
        for (ChatCompletionsToolCall chatCompletionsToolCall : chatCompletionsToolCalls) {
            ToolCall toolCall = toToolCall(chatCompletionsToolCall, defaultTurnId);
            if (toolCall != null) {
                toolCalls.add(toolCall);
            }
        }
        return toolCalls;
    }

    protected ToolCall toToolCall(ChatCompletionsToolCall chatCompletionsToolCall, String defaultTurnId) {
        if (chatCompletionsToolCall == null || chatCompletionsToolCall.getFunction() == null) {
            return null;
        }
        String toolName = chatCompletionsToolCall.getFunction().getName();
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        String toolCallId = resolveToolCallId(chatCompletionsToolCall, 0);
        return ToolCall.builder()
            .toolCallId(toolCallId)
            .toolName(toolName)
            .argumentsJson(Optional.ofNullable(chatCompletionsToolCall.getFunction().getArguments()).orElse("{}"))
            .turnId(defaultTurnId)
            .build();
    }

    protected void mergeStreamToolCalls(
        Map<String, StreamingToolCallAccumulator> toolCallStateMap,
        List<ChatCompletionsToolCall> chatCompletionsToolCalls,
        String defaultTurnId
    ) {
        if (chatCompletionsToolCalls == null || chatCompletionsToolCalls.isEmpty()) {
            return;
        }
        for (ChatCompletionsToolCall chatCompletionsToolCall : chatCompletionsToolCalls) {
            if (chatCompletionsToolCall == null) {
                continue;
            }
            String stateKey = resolveStreamStateKey(chatCompletionsToolCall, toolCallStateMap.size());
            StreamingToolCallAccumulator state = toolCallStateMap.computeIfAbsent(stateKey, key -> new StreamingToolCallAccumulator());

            if (state.toolCallId == null || state.toolCallId.isBlank()) {
                state.toolCallId = resolveToolCallId(chatCompletionsToolCall, toolCallStateMap.size());
            }
            if (state.turnId == null || state.turnId.isBlank()) {
                state.turnId = defaultTurnId;
            }

            if (chatCompletionsToolCall.getFunction() != null) {
                if (chatCompletionsToolCall.getFunction().getName() != null && !chatCompletionsToolCall.getFunction().getName().isBlank()) {
                    state.toolName = chatCompletionsToolCall.getFunction().getName();
                }
                if (chatCompletionsToolCall.getFunction().getArguments() != null && !chatCompletionsToolCall.getFunction().getArguments().isEmpty()) {
                    state.argumentsBuilder.append(chatCompletionsToolCall.getFunction().getArguments());
                }
            }
        }
    }

    protected List<ToolCall> toToolCalls(Map<String, StreamingToolCallAccumulator> toolCallStateMap, String defaultTurnId) {
        if (toolCallStateMap == null || toolCallStateMap.isEmpty()) {
            return List.of();
        }
        List<ToolCall> toolCalls = new ArrayList<>();
        for (StreamingToolCallAccumulator state : toolCallStateMap.values()) {
            if (state.toolName == null || state.toolName.isBlank()) {
                continue;
            }
            String argumentsJson = state.argumentsBuilder.length() == 0 ? "{}" : state.argumentsBuilder.toString();
            toolCalls.add(
                ToolCall.builder()
                    .toolCallId(Optional.ofNullable(state.toolCallId).orElse(providerKey() + "-tool-call"))
                    .toolName(state.toolName)
                    .argumentsJson(argumentsJson)
                    .turnId(Optional.ofNullable(state.turnId).orElse(defaultTurnId))
                    .build()
            );
        }
        return toolCalls;
    }

    protected String resolveStreamStateKey(ChatCompletionsToolCall chatCompletionsToolCall, int sequence) {
        if (chatCompletionsToolCall.getId() != null && !chatCompletionsToolCall.getId().isBlank()) {
            return chatCompletionsToolCall.getId();
        }
        if (chatCompletionsToolCall.getIndex() != null) {
            return providerKey() + "-idx-" + chatCompletionsToolCall.getIndex();
        }
        return providerKey() + "-seq-" + sequence;
    }

    protected String resolveToolCallId(ChatCompletionsToolCall chatCompletionsToolCall, int sequence) {
        if (chatCompletionsToolCall.getId() != null && !chatCompletionsToolCall.getId().isBlank()) {
            return chatCompletionsToolCall.getId();
        }
        if (chatCompletionsToolCall.getIndex() != null) {
            return providerKey() + "-tool-call-" + chatCompletionsToolCall.getIndex();
        }
        return providerKey() + "-tool-call-" + sequence;
    }

    protected String endpoint() {
        String base = Optional.ofNullable(baseUrl()).orElse("").trim();
        String path = Optional.ofNullable(chatPath()).orElse("").trim();
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
        ValidationUtils.requireNonBlank(baseUrl(), providerKey() + ".baseUrl");
        ValidationUtils.requireNonBlank(apiKey(), providerKey() + ".apiKey");
        ValidationUtils.requireNonBlank(model(), providerKey() + ".model");
    }

    protected abstract String providerName();

    protected abstract String providerKey();

    protected abstract String baseUrl();

    protected abstract String chatPath();

    protected abstract String apiKey();

    protected abstract String model();

    protected abstract int connectTimeoutMs();

    protected abstract int readTimeoutMs();
}

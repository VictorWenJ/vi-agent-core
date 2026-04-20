package com.vi.agent.core.infra.provider.base;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.common.util.ValidationUtils;
import com.vi.agent.core.infra.provider.http.HttpRequestOptions;
import com.vi.agent.core.infra.provider.http.LlmHttpExecutor;
import com.vi.agent.core.infra.provider.protocol.openai.*;
import com.vi.agent.core.model.llm.*;
import com.vi.agent.core.model.message.*;
import com.vi.agent.core.model.port.LlmGateway;
import com.vi.agent.core.model.tool.ToolDefinition;
import jakarta.annotation.Resource;

import java.util.*;
import java.util.function.Consumer;

/**
 * Shared OpenAI-compatible provider implementation.
 */
public abstract class OpenAICompatibleChatProvider implements LlmGateway {

    @Resource
    protected LlmHttpExecutor httpExecutor;

    @Override
    public ModelResponse generate(ModelRequest modelRequest) {
        assertConfigured();
        ChatCompletionsRequest request = buildRequest(modelRequest, false);
        String payload = JsonUtils.toJson(request);

        try {
            String responseBody = httpExecutor.post(endpoint(), defaultHeaders(), payload, requestOptions());
            return parseModelResponse(responseBody);
        } catch (AgentRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, providerName() + " sync call failed", e);
        }
    }

    @Override
    public ModelResponse generateStreaming(ModelRequest modelRequest, Consumer<String> chunkConsumer) {
        assertConfigured();
        ChatCompletionsRequest request = buildRequest(modelRequest, true);
        request.setStreamOptions(new ChatCompletionsStreamOptions());
        request.getStreamOptions().setIncludeUsage(true);

        String payload = JsonUtils.toJson(request);

        StringBuilder fullContent = new StringBuilder();
        Map<String, StreamingToolCallAccumulator> toolCallStateMap = new LinkedHashMap<>();
        UsageInfo usage = UsageInfo.empty();
        FinishReason finishReason = FinishReason.STOP;
        String modelName = model();

        try {
            final UsageInfo[] usageRef = new UsageInfo[] {usage};
            final FinishReason[] reasonRef = new FinishReason[] {finishReason};
            final String[] modelRef = new String[] {modelName};
            httpExecutor.postStream(
                endpoint(),
                defaultHeaders(),
                payload,
                requestOptions(),
                line -> {
                    ChatCompletionsStreamChunk chunk = parseStreamLine(line, fullContent, toolCallStateMap, chunkConsumer);
                    if (chunk == null) {
                        return;
                    }
                    if (chunk.getUsage() != null) {
                        usageRef[0] = toUsageInfo(chunk.getUsage(), providerKey(), chunk.getModel() == null ? model() : chunk.getModel());
                    }
                    if (chunk.getModel() != null) {
                        modelRef[0] = chunk.getModel();
                    }
                    if (chunk.getChoices() != null) {
                        for (ChatCompletionsStreamChoice choice : chunk.getChoices()) {
                            if (choice != null && choice.getFinishReason() != null && !choice.getFinishReason().isBlank()) {
                                reasonRef[0] = toFinishReason(choice.getFinishReason());
                            }
                        }
                    }
                }
            );

            return ModelResponse.builder()
                .content(fullContent.toString())
                .toolCalls(toModelToolCalls(toolCallStateMap))
                .finishReason(reasonRef[0])
                .usage(usageRef[0])
                .provider(providerKey())
                .model(modelRef[0])
                .build();
        } catch (AgentRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, providerName() + " stream call failed", e);
        }
    }

    protected ChatCompletionsRequest buildRequest(ModelRequest modelRequest, boolean stream) {
        ChatCompletionsRequest request = new ChatCompletionsRequest();
        request.setModel(model());
        request.setStream(stream);

        List<ChatCompletionsMessage> apiMessages = new ArrayList<>();
        if (modelRequest.getMessages() != null) {
            for (Message message : modelRequest.getMessages()) {
                ChatCompletionsMessage apiMessage = toApiMessage(message);
                if (apiMessage != null) {
                    apiMessages.add(apiMessage);
                }
            }
        }
        request.setMessages(apiMessages);

        if (modelRequest.getTools() != null && !modelRequest.getTools().isEmpty()) {
            List<ChatCompletionsToolDefinition> tools = new ArrayList<>();
            for (ToolDefinition definition : modelRequest.getTools()) {
                ChatCompletionsToolDefinition tool = toApiToolDefinition(definition);
                if (tool != null) {
                    tools.add(tool);
                }
            }
            if (!tools.isEmpty()) {
                request.setTools(tools);
                request.setToolChoice("auto");
            }
        }

        return request;
    }

    protected ChatCompletionsMessage toApiMessage(Message message) {
        if (message == null) {
            return null;
        }

        if (message instanceof ToolResultMessage toolResultMessage) {
            ChatCompletionsMessage api = new ChatCompletionsMessage();
            api.setRole("tool");
            api.setContent(toolResultMessage.getContent());
            api.setToolCallId(toolResultMessage.getToolCallId());
            api.setName(toolResultMessage.getToolName());
            return api;
        }

        ChatCompletionsMessage api = new ChatCompletionsMessage();
        api.setRole(toApiRole(message.getRole()));
        api.setContent(message.getContent());

        if (message instanceof AssistantMessage assistantMessage
            && assistantMessage.getToolCalls() != null
            && !assistantMessage.getToolCalls().isEmpty()) {
            List<ChatCompletionsToolCall> toolCalls = new ArrayList<>();
            for (ModelToolCall toolCall : assistantMessage.getToolCalls()) {
                ChatCompletionsToolCall apiToolCall = new ChatCompletionsToolCall();
                apiToolCall.setId(toolCall.getToolCallId());
                apiToolCall.setType("function");
                ChatCompletionsFunction function = new ChatCompletionsFunction();
                function.setName(toolCall.getToolName());
                function.setArguments(toolCall.getArgumentsJson());
                apiToolCall.setFunction(function);
                toolCalls.add(apiToolCall);
            }
            api.setToolCalls(toolCalls);
        }

        if (message instanceof ToolCallMessage toolCallMessage) {
            List<ChatCompletionsToolCall> toolCalls = new ArrayList<>();
            ChatCompletionsToolCall apiToolCall = new ChatCompletionsToolCall();
            apiToolCall.setId(toolCallMessage.getToolCallId());
            apiToolCall.setType("function");
            ChatCompletionsFunction function = new ChatCompletionsFunction();
            function.setName(toolCallMessage.getToolName());
            function.setArguments(toolCallMessage.getArgumentsJson());
            apiToolCall.setFunction(function);
            toolCalls.add(apiToolCall);
            api.setRole("assistant");
            api.setToolCalls(toolCalls);
            api.setContent(null);
        }

        return api;
    }

    protected ChatCompletionsToolDefinition toApiToolDefinition(ToolDefinition definition) {
        if (definition == null || definition.getName() == null || definition.getName().isBlank()) {
            return null;
        }

        ChatCompletionsToolDefinition toolDefinition = new ChatCompletionsToolDefinition();
        toolDefinition.setType("function");

        ChatCompletionsFunction function = new ChatCompletionsFunction();
        function.setName(definition.getName());
        function.setDescription(Optional.ofNullable(definition.getDescription()).orElse(""));

        Object parameters = JsonUtils.jsonToBean(
            Optional.ofNullable(definition.getParametersJson()).orElse("{}"),
            Object.class
        );
        function.setParameters(parameters == null ? Map.of("type", "object", "properties", Map.of()) : parameters);

        toolDefinition.setFunction(function);
        return toolDefinition;
    }

    protected ModelResponse parseModelResponse(String body) {
        ChatCompletionsResponse response = JsonUtils.jsonToBean(body, ChatCompletionsResponse.class);
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, providerName() + " empty response");
        }

        ChatCompletionsChoice choice = response.getChoices().get(0);
        if (choice == null || choice.getMessage() == null) {
            throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, providerName() + " missing message");
        }

        ChatCompletionsMessage message = choice.getMessage();
        List<ModelToolCall> toolCalls = toModelToolCalls(message.getToolCalls());

        return ModelResponse.builder()
            .content(Optional.ofNullable(message.getContent()).orElse(""))
            .toolCalls(toolCalls)
            .finishReason(toFinishReason(choice.getFinishReason()))
            .usage(toUsageInfo(response.getUsage(), providerKey(), response.getModel()))
            .provider(providerKey())
            .model(response.getModel() == null ? model() : response.getModel())
            .build();
    }

    protected ChatCompletionsStreamChunk parseStreamLine(
        String line,
        StringBuilder fullContent,
        Map<String, StreamingToolCallAccumulator> toolCallStateMap,
        Consumer<String> chunkConsumer
    ) {
        if (line == null || line.isBlank() || !line.startsWith("data:")) {
            return null;
        }

        String data = line.substring("data:".length()).trim();
        if ("[DONE]".equals(data)) {
            return null;
        }

        ChatCompletionsStreamChunk streamChunk = JsonUtils.jsonToBean(data, ChatCompletionsStreamChunk.class);
        if (streamChunk == null) {
            return null;
        }

        if (streamChunk.getChoices() == null || streamChunk.getChoices().isEmpty()) {
            return streamChunk;
        }

        for (ChatCompletionsStreamChoice choice : streamChunk.getChoices()) {
            if (choice == null || choice.getDelta() == null) {
                continue;
            }
            ChatCompletionsDelta delta = choice.getDelta();
            if (delta.getContent() != null && !delta.getContent().isBlank()) {
                fullContent.append(delta.getContent());
                if (chunkConsumer != null) {
                    chunkConsumer.accept(delta.getContent());
                }
            }
            mergeStreamToolCalls(toolCallStateMap, delta.getToolCalls(), toolCallStateMap.size());
        }
        return streamChunk;
    }

    protected void mergeStreamToolCalls(
        Map<String, StreamingToolCallAccumulator> stateMap,
        List<ChatCompletionsToolCall> toolCalls,
        int seqStart
    ) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }
        int seq = seqStart;
        for (ChatCompletionsToolCall toolCall : toolCalls) {
            if (toolCall == null) {
                continue;
            }
            String key = resolveStreamStateKey(toolCall, seq++);
            StreamingToolCallAccumulator state = stateMap.computeIfAbsent(key, ignored -> new StreamingToolCallAccumulator());
            if (state.toolCallId == null || state.toolCallId.isBlank()) {
                state.toolCallId = resolveToolCallId(toolCall, stateMap.size());
            }
            if (toolCall.getFunction() != null) {
                if (toolCall.getFunction().getName() != null && !toolCall.getFunction().getName().isBlank()) {
                    state.toolName = toolCall.getFunction().getName();
                }
                if (toolCall.getFunction().getArguments() != null) {
                    state.argumentsBuilder.append(toolCall.getFunction().getArguments());
                }
            }
        }
    }

    protected String resolveStreamStateKey(ChatCompletionsToolCall toolCall, int sequence) {
        if (toolCall.getId() != null && !toolCall.getId().isBlank()) {
            return toolCall.getId();
        }
        if (toolCall.getIndex() != null) {
            return providerKey() + "-idx-" + toolCall.getIndex();
        }
        return providerKey() + "-seq-" + sequence;
    }

    protected String resolveToolCallId(ChatCompletionsToolCall toolCall, int sequence) {
        if (toolCall.getId() != null && !toolCall.getId().isBlank()) {
            return toolCall.getId();
        }
        if (toolCall.getIndex() != null) {
            return providerKey() + "-tool-call-" + toolCall.getIndex();
        }
        return providerKey() + "-tool-call-" + sequence;
    }

    protected List<ModelToolCall> toModelToolCalls(List<ChatCompletionsToolCall> apiToolCalls) {
        if (apiToolCalls == null || apiToolCalls.isEmpty()) {
            return List.of();
        }
        List<ModelToolCall> modelToolCalls = new ArrayList<>();
        int seq = 0;
        for (ChatCompletionsToolCall apiToolCall : apiToolCalls) {
            if (apiToolCall == null || apiToolCall.getFunction() == null) {
                continue;
            }
            String name = apiToolCall.getFunction().getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            modelToolCalls.add(ModelToolCall.builder()
                .toolCallId(resolveToolCallId(apiToolCall, seq++))
                .toolName(name)
                .argumentsJson(Optional.ofNullable(apiToolCall.getFunction().getArguments()).orElse("{}"))
                .build());
        }
        return modelToolCalls;
    }

    protected List<ModelToolCall> toModelToolCalls(Map<String, StreamingToolCallAccumulator> stateMap) {
        if (stateMap == null || stateMap.isEmpty()) {
            return List.of();
        }
        List<ModelToolCall> toolCalls = new ArrayList<>();
        for (StreamingToolCallAccumulator value : stateMap.values()) {
            if (value == null || value.toolName == null || value.toolName.isBlank()) {
                continue;
            }
            toolCalls.add(ModelToolCall.builder()
                .toolCallId(value.toolCallId)
                .toolName(value.toolName)
                .argumentsJson(value.argumentsBuilder.length() == 0 ? "{}" : value.argumentsBuilder.toString())
                .build());
        }
        return toolCalls;
    }

    protected UsageInfo toUsageInfo(ChatCompletionsUsage usage, String provider, String modelName) {
        if (usage == null) {
            return UsageInfo.builder().provider(provider).model(modelName == null ? model() : modelName).build();
        }
        return UsageInfo.builder()
            .inputTokens(usage.getPromptTokens())
            .outputTokens(usage.getCompletionTokens())
            .totalTokens(usage.getTotalTokens())
            .provider(provider)
            .model(modelName == null ? model() : modelName)
            .build();
    }

    protected FinishReason toFinishReason(String finishReason) {
        if (finishReason == null || finishReason.isBlank()) {
            return FinishReason.STOP;
        }
        return switch (finishReason.toLowerCase(Locale.ROOT)) {
            case "tool_calls", "tool_call", "function_call" -> FinishReason.TOOL_CALL;
            case "length" -> FinishReason.LENGTH;
            case "error" -> FinishReason.ERROR;
            case "cancelled" -> FinishReason.CANCELLED;
            default -> FinishReason.STOP;
        };
    }

    protected String toApiRole(MessageRole role) {
        return switch (role) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
            case SYSTEM -> "system";
            case SUMMARY -> "system";
        };
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

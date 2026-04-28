package com.vi.agent.core.infra.provider.base;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.common.util.ValidationUtils;
import com.vi.agent.core.infra.provider.ProviderStructuredOutputCapability;
import com.vi.agent.core.infra.provider.ProviderStructuredOutputCapabilityValidator;
import com.vi.agent.core.infra.provider.ProviderStructuredOutputSelection;
import com.vi.agent.core.infra.provider.ProviderStructuredSchemaCompiler;
import com.vi.agent.core.infra.provider.StructuredOutputRequestAdapter;
import com.vi.agent.core.infra.provider.StructuredOutputResponseExtractor;
import com.vi.agent.core.infra.provider.http.HttpRequestOptions;
import com.vi.agent.core.infra.provider.openai.OpenAICompatibleMessageProjector;
import com.vi.agent.core.infra.provider.http.LlmHttpExecutor;
import com.vi.agent.core.infra.provider.protocol.openai.*;
import com.vi.agent.core.model.llm.*;
import com.vi.agent.core.model.port.LlmGateway;
import com.vi.agent.core.model.tool.ToolDefinition;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.function.Consumer;

/**
 * Shared OpenAI-compatible provider implementation.
 */
@Slf4j
public abstract class OpenAICompatibleChatProvider implements LlmGateway {

    /** provider schema view 编译器。 */
    private final ProviderStructuredSchemaCompiler structuredSchemaCompiler = new ProviderStructuredSchemaCompiler();

    /** provider structured output mode 请求前选择器。 */
    private final ProviderStructuredOutputCapabilityValidator structuredOutputCapabilityValidator =
        new ProviderStructuredOutputCapabilityValidator(structuredSchemaCompiler);

    /** OpenAI-compatible structured output 请求适配器。 */
    private final StructuredOutputRequestAdapter structuredOutputRequestAdapter = new StructuredOutputRequestAdapter();

    /** OpenAI-compatible structured output 响应归一化器。 */
    private final StructuredOutputResponseExtractor structuredOutputResponseExtractor = new StructuredOutputResponseExtractor();

    @Resource
    protected LlmHttpExecutor httpExecutor;

    @Resource
    protected OpenAICompatibleMessageProjector messageProjector;

    @Override
    public ModelResponse generate(ModelRequest modelRequest) {
        assertConfigured();
        assertStructuredOutputDoesNotMixBusinessTools(modelRequest);
        ProviderStructuredOutputSelection structuredOutputSelection = selectStructuredOutput(modelRequest);
        ChatCompletionsRequest request = buildRequest(modelRequest, false, structuredOutputSelection);
        String payload = JsonUtils.toJson(request);

        try {
            log.info("OpenAICompatibleChatProvider generate payload={}", payload);
            String responseBody = httpExecutor.post(endpoint(), defaultHeaders(), payload, requestOptions());
            log.info("OpenAICompatibleChatProvider generate responseBody={}", responseBody);
            return parseModelResponse(responseBody, structuredOutputSelection);
        } catch (AgentRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, providerName() + " sync call failed", e);
        }
    }

    @Override
    public ModelResponse generateStreaming(ModelRequest modelRequest, Consumer<String> chunkConsumer) {
        assertConfigured();
        if (modelRequest != null && modelRequest.getStructuredOutputContract() != null) {
            throw new AgentRuntimeException(
                ErrorCode.PROVIDER_CALL_FAILED,
                providerName() + " streaming structured output is not supported in P2-E3"
            );
        }
        ChatCompletionsRequest request = buildRequest(modelRequest, true);
        request.setStreamOptions(new ChatCompletionsStreamOptions());
        request.getStreamOptions().setIncludeUsage(true);

        String payload = JsonUtils.toJson(request);
        log.info("OpenAICompatibleChatProvider generateStreaming payload={}", payload);

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
        ProviderStructuredOutputSelection structuredOutputSelection = selectStructuredOutput(modelRequest);
        return buildRequest(modelRequest, stream, structuredOutputSelection);
    }

    protected ChatCompletionsRequest buildRequest(
        ModelRequest modelRequest,
        boolean stream,
        ProviderStructuredOutputSelection structuredOutputSelection
    ) {
        ChatCompletionsRequest request = new ChatCompletionsRequest();
        request.setModel(model());
        request.setStream(stream);

        List<ChatCompletionsMessage> apiMessages = messageProjector.project(modelRequest.getMessages());
        request.setMessages(apiMessages);

        if (CollectionUtils.isNotEmpty(modelRequest.getTools())) {
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

        customizeRequest(request, modelRequest, stream);
        structuredOutputRequestAdapter.apply(request, structuredOutputSelection);
        return request;
    }

    /**
     * 子类定制 OpenAI-compatible 请求的扩展点。
     */
    protected void customizeRequest(ChatCompletionsRequest request, ModelRequest modelRequest, boolean stream) {
        // 默认无 provider-specific 额外字段。
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
        return parseModelResponse(body, ProviderStructuredOutputSelection.disabled());
    }

    protected ModelResponse parseModelResponse(String body, ProviderStructuredOutputSelection structuredOutputSelection) {
        ChatCompletionsResponse response = JsonUtils.jsonToBean(body, ChatCompletionsResponse.class);
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, providerName() + " empty response");
        }

        ChatCompletionsChoice choice = response.getChoices().get(0);
        if (choice == null || choice.getMessage() == null) {
            throw new AgentRuntimeException(ErrorCode.PROVIDER_CALL_FAILED, providerName() + " missing message");
        }

        ChatCompletionsMessage message = choice.getMessage();
        List<ModelToolCall> toolCalls = toModelToolCalls(message.getToolCalls(), structuredOutputSelection);
        StructuredOutputChannelResult structuredOutputChannelResult = Boolean.TRUE.equals(structuredOutputSelection.getEnabled())
            ? structuredOutputResponseExtractor.extract(response, structuredOutputSelection, providerKey(), response.getModel() == null ? model() : response.getModel())
            : null;

        return ModelResponse.builder()
            .content(Optional.ofNullable(message.getContent()).orElse(""))
            .toolCalls(toolCalls)
            .finishReason(toFinishReason(choice.getFinishReason()))
            .usage(toUsageInfo(response.getUsage(), providerKey(), response.getModel()))
            .provider(providerKey())
            .model(response.getModel() == null ? model() : response.getModel())
            .structuredOutputChannelResult(structuredOutputChannelResult)
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
        return toModelToolCalls(apiToolCalls, ProviderStructuredOutputSelection.disabled());
    }

    protected List<ModelToolCall> toModelToolCalls(
        List<ChatCompletionsToolCall> apiToolCalls,
        ProviderStructuredOutputSelection structuredOutputSelection
    ) {
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
            if (Boolean.TRUE.equals(structuredOutputSelection.getEnabled())
                && name.equals(structuredOutputSelection.getFunctionName())) {
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
                .argumentsJson(value.argumentsBuilder.isEmpty() ? "{}" : value.argumentsBuilder.toString())
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

    protected void assertStructuredOutputDoesNotMixBusinessTools(ModelRequest modelRequest) {
        if (modelRequest != null
            && modelRequest.getStructuredOutputContract() != null
            && CollectionUtils.isNotEmpty(modelRequest.getTools())) {
            throw new AgentRuntimeException(
                ErrorCode.PROVIDER_CALL_FAILED,
                "P2-E3 structured output cannot be mixed with business tools"
            );
        }
    }

    protected ProviderStructuredOutputSelection selectStructuredOutput(ModelRequest modelRequest) {
        return structuredOutputCapabilityValidator.select(modelRequest, structuredOutputCapability());
    }

    protected ProviderStructuredOutputCapability structuredOutputCapability() {
        return switch (providerKey()) {
            case "openai" -> ProviderStructuredOutputCapability.builder()
                .providerName(providerKey())
                .modelName(model())
                .supportsStrictToolCall(true)
                .supportsJsonSchemaResponseFormat(true)
                .supportsJsonObject(true)
                .build();
            default -> ProviderStructuredOutputCapability.jsonObjectOnly(providerKey(), model());
        };
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

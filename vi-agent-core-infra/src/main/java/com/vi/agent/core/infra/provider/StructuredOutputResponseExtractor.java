package com.vi.agent.core.infra.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vi.agent.core.infra.provider.protocol.openai.ChatCompletionsChoice;
import com.vi.agent.core.infra.provider.protocol.openai.ChatCompletionsMessage;
import com.vi.agent.core.infra.provider.protocol.openai.ChatCompletionsResponse;
import com.vi.agent.core.infra.provider.protocol.openai.ChatCompletionsToolCall;
import com.vi.agent.core.model.llm.NormalizedStructuredLlmOutput;
import com.vi.agent.core.model.llm.StructuredOutputChannelResult;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * 从 OpenAI-compatible provider response 中归一化结构化输出通道。
 */
public class StructuredOutputResponseExtractor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 提取并归一化结构化输出。
     */
    public StructuredOutputChannelResult extract(
        ChatCompletionsResponse response,
        ProviderStructuredOutputSelection selection,
        String providerName,
        String modelName
    ) {
        if (selection == null || !Boolean.TRUE.equals(selection.getEnabled())) {
            return failure(selection, "structured output is not enabled");
        }
        try {
            ChatCompletionsMessage message = firstMessage(response);
            if (message == null) {
                return failure(selection, "structured output message is missing");
            }
            String outputJson = selection.getSelectedStructuredOutputMode() == StructuredLlmOutputMode.STRICT_TOOL_CALL
                ? extractToolCallArguments(message, selection)
                : message.getContent();
            if (StringUtils.isBlank(outputJson)) {
                return failure(selection, "structured output content is blank");
            }
            String normalizedJson = normalizeJsonObject(outputJson);
            JsonNode outputNode = OBJECT_MAPPER.readTree(normalizedJson);
            if (!outputNode.isObject()) {
                return failure(selection, "structured output must be JSON object");
            }
            NormalizedStructuredLlmOutput output = NormalizedStructuredLlmOutput.builder()
                .structuredOutputContractKey(selection.getStructuredOutputContractKey())
                .actualStructuredOutputMode(selection.getSelectedStructuredOutputMode())
                .outputJson(OBJECT_MAPPER.writeValueAsString(outputNode))
                .providerName(providerName)
                .modelName(response != null && StringUtils.isNotBlank(response.getModel()) ? response.getModel() : modelName)
                .providerResponseId(response == null ? null : response.getId())
                .build();
            return StructuredOutputChannelResult.builder()
                .success(true)
                .output(output)
                .actualStructuredOutputMode(selection.getSelectedStructuredOutputMode())
                .retryCount(0)
                .build();
        } catch (Exception ex) {
            return failure(selection, "structured output normalization failed: " + ex.getMessage());
        }
    }

    private ChatCompletionsMessage firstMessage(ChatCompletionsResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.getChoices())) {
            return null;
        }
        ChatCompletionsChoice choice = response.getChoices().get(0);
        return choice == null ? null : choice.getMessage();
    }

    private String extractToolCallArguments(
        ChatCompletionsMessage message,
        ProviderStructuredOutputSelection selection
    ) {
        if (CollectionUtils.isEmpty(message.getToolCalls())) {
            throw new IllegalStateException("structured output tool_call is missing");
        }
        for (ChatCompletionsToolCall toolCall : message.getToolCalls()) {
            if (toolCall == null || toolCall.getFunction() == null) {
                continue;
            }
            if (selection.getFunctionName().equals(toolCall.getFunction().getName())) {
                return toolCall.getFunction().getArguments();
            }
        }
        throw new IllegalStateException("structured output function is missing: " + selection.getFunctionName());
    }

    /**
     * 最小化清洗 provider 返回的 JSON object 文本。
     */
    private String normalizeJsonObject(String rawJson) {
        String normalized = StringUtils.defaultString(rawJson);
        if (!normalized.isEmpty() && normalized.charAt(0) == '\uFEFF') {
            normalized = normalized.substring(1);
        }
        normalized = normalized.trim();
        if (normalized.startsWith("```")) {
            int firstLineEnd = normalized.indexOf('\n');
            int lastFenceStart = normalized.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFenceStart > firstLineEnd) {
                normalized = normalized.substring(firstLineEnd + 1, lastFenceStart).trim();
            }
        }
        return normalized;
    }

    private StructuredOutputChannelResult failure(ProviderStructuredOutputSelection selection, String failureReason) {
        return StructuredOutputChannelResult.builder()
            .success(false)
            .actualStructuredOutputMode(selection == null ? null : selection.getSelectedStructuredOutputMode())
            .retryCount(0)
            .failureReason(failureReason)
            .build();
    }
}

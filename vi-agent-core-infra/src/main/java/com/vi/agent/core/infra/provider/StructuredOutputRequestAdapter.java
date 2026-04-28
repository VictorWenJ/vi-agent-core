package com.vi.agent.core.infra.provider;

import com.vi.agent.core.infra.provider.protocol.openai.ChatCompletionsFunction;
import com.vi.agent.core.infra.provider.protocol.openai.ChatCompletionsRequest;
import com.vi.agent.core.infra.provider.protocol.openai.ChatCompletionsToolDefinition;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 将结构化输出选择结果写入 OpenAI-compatible 请求。
 */
public class StructuredOutputRequestAdapter {

    /**
     * 根据选定 mode 改写 provider 请求。
     */
    public void apply(ChatCompletionsRequest request, ProviderStructuredOutputSelection selection) {
        if (request == null || selection == null || !Boolean.TRUE.equals(selection.getEnabled())) {
            return;
        }
        StructuredLlmOutputMode mode = selection.getSelectedStructuredOutputMode();
        if (mode == StructuredLlmOutputMode.STRICT_TOOL_CALL) {
            applyStrictToolCall(request, selection);
            return;
        }
        if (mode == StructuredLlmOutputMode.JSON_SCHEMA_RESPONSE_FORMAT) {
            request.setResponseFormat(Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                    "name", selection.getFunctionName(),
                    "strict", Boolean.TRUE,
                    "schema", selection.getProviderSchemaView()
                )
            ));
            return;
        }
        if (mode == StructuredLlmOutputMode.JSON_OBJECT) {
            request.setResponseFormat(Map.of("type", "json_object"));
        }
    }

    private void applyStrictToolCall(ChatCompletionsRequest request, ProviderStructuredOutputSelection selection) {
        List<ChatCompletionsToolDefinition> tools = request.getTools() == null
            ? new ArrayList<>()
            : new ArrayList<>(request.getTools());
        ChatCompletionsToolDefinition toolDefinition = new ChatCompletionsToolDefinition();
        toolDefinition.setType("function");

        ChatCompletionsFunction function = new ChatCompletionsFunction();
        function.setName(selection.getFunctionName());
        function.setDescription(selection.getFunctionDescription());
        function.setParameters(selection.getProviderSchemaView());
        function.setStrict(true);
        toolDefinition.setFunction(function);

        tools.add(toolDefinition);
        request.setTools(tools);
        request.setToolChoice(Map.of(
            "type", "function",
            "function", Map.of("name", selection.getFunctionName())
        ));
        request.setParallelToolCalls(false);
    }
}

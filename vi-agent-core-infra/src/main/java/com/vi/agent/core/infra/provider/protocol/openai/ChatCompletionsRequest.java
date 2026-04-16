package com.vi.agent.core.infra.provider.protocol.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Chat Completion 请求体。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionsRequest {

    /**
     * 模型名称。
     */
    private String model;

    /**
     * 消息列表。
     */
    private List<ChatCompletionsMessage> messages;

    /**
     * 是否开启流式返回。
     */
    private Boolean stream;

    /**
     * 工具定义列表。
     */
    private List<ChatCompletionsToolDefinition> tools;

    /**
     * 工具选择策略。
     */
    @JsonProperty("tool_choice")
    private Object toolChoice;

    /**
     * 是否允许并行工具调用。
     */
    @JsonProperty("parallel_tool_calls")
    private Boolean parallelToolCalls;

    /**
     * 流式附加选项。
     */
    @JsonProperty("stream_options")
    private ChatCompletionsStreamOptions streamOptions;
}

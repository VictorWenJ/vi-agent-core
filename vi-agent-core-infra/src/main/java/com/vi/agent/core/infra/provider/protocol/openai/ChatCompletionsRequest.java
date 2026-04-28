package com.vi.agent.core.infra.provider.protocol.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Chat Completion request.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionsRequest {

    /**
     * 模型名.
     */
    private String model;

    /**
     * 消息列表.
     */
    private List<ChatCompletionsMessage> messages;

    /**
     * 是否流式返回.
     */
    private Boolean stream;

    /**
     * 工具定义列表.
     */
    private List<ChatCompletionsToolDefinition> tools;

    /**
     * 工具选择策略.
     */
    @JsonProperty("tool_choice")
    private Object toolChoice;

    /**
     * 是否并行工具调用.
     */
    @JsonProperty("parallel_tool_calls")
    private Boolean parallelToolCalls;

    /**
     * 流式配置.
     */
    @JsonProperty("stream_options")
    private ChatCompletionsStreamOptions streamOptions;

    /**
     * Provider 响应格式约束。
     */
    @JsonProperty("response_format")
    private Object responseFormat;

    /**
     * 思考类型（DeepSeek）.
     */
    @JsonProperty("thinking_type")
    private String thinkingType;

    /**
     * 最大输出 token 数（DeepSeek）.
     */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /**
     * 温度参数（DeepSeek）.
     */
    private Double temperature;
}

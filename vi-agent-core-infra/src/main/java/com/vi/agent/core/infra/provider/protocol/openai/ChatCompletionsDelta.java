package com.vi.agent.core.infra.provider.protocol.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 流式 delta。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatCompletionsDelta {

    /**
     * 增量文本。
     */
    private String content;

    /**
     * 推理内容。
     */
    @JsonProperty("reasoning_content")
    private String reasoningContent;

    /**
     * 工具调用增量。
     */
    @JsonProperty("tool_calls")
    private List<ChatCompletionsToolCall> toolCalls;
}

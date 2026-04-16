package com.vi.agent.core.infra.provider.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ApiMessage {

    /**
     * 角色。
     */
    private String role;

    /**
     * 文本内容。
     */
    private String content;

    /**
     * tool call id。
     */
    @JsonProperty("tool_call_id")
    private String toolCallId;

    /**
     * 工具名称。
     */
    private String name;

    /**
     * 工具调用。
     */
    @JsonProperty("tool_calls")
    private List<ApiToolCall> toolCalls;
}

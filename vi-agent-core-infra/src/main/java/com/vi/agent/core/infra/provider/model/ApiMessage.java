package com.vi.agent.core.infra.provider.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 对话消息。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiMessage {

    /**
     * 角色。
     */
    private String role;

    /**
     * 内容。
     */
    private String content;

    /**
     * 参与者名称。
     */
    private String name;

    /**
     * 工具调用 ID。
     */
    @JsonProperty("tool_call_id")
    private String toolCallId;

    /**
     * 工具调用列表。
     */
    @JsonProperty("tool_calls")
    private List<ApiToolCall> toolCalls;
}

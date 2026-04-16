package com.vi.agent.core.infra.provider.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ApiDelta {

    /**
     * 增量文本。
     */
    private String content;

    /**
     * 增量工具调用。
     */
    @JsonProperty("tool_calls")
    private List<ApiToolCall> toolCalls;
}

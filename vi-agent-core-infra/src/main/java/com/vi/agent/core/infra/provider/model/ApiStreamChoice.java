package com.vi.agent.core.infra.provider.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ApiStreamChoice {

    /**
     * 增量消息。
     */
    private ApiDelta delta;

    /**
     * 结束原因。
     */
    @JsonProperty("finish_reason")
    private String finishReason;
}

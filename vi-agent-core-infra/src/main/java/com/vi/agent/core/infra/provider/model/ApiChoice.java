package com.vi.agent.core.infra.provider.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ApiChoice {

    /**
     * 消息内容。
     */
    private ApiMessage message;

    /**
     * 结束原因。
     */
    @JsonProperty("finish_reason")
    private String finishReason;
}

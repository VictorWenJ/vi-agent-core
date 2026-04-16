package com.vi.agent.core.infra.provider.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 非流式 choice。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiChoice {

    /**
     * 索引。
     */
    private Integer index;

    /**
     * 完成原因。
     */
    @JsonProperty("finish_reason")
    private String finishReason;

    /**
     * 输出消息。
     */
    private ApiMessage message;
}

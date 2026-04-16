package com.vi.agent.core.infra.provider.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 流式 choice。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiStreamChoice {

    /**
     * 索引。
     */
    private Integer index;

    /**
     * 增量内容。
     */
    private ApiDelta delta;

    /**
     * 完成原因。
     */
    @JsonProperty("finish_reason")
    private String finishReason;
}

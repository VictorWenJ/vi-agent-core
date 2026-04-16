package com.vi.agent.core.infra.provider.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 流式附加选项。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiStreamOptions {

    /**
     * 是否在最终块附带 usage 信息。
     */
    @JsonProperty("include_usage")
    private Boolean includeUsage;
}

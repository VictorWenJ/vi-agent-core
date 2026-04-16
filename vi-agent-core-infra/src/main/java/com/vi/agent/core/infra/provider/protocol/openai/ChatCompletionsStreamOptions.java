package com.vi.agent.core.infra.provider.protocol.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 流式附加选项。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionsStreamOptions {

    /**
     * 是否在最终块附带 usage 信息。
     */
    @JsonProperty("include_usage")
    private Boolean includeUsage;
}

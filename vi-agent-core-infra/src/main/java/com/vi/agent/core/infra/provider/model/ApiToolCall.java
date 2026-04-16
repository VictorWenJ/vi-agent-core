package com.vi.agent.core.infra.provider.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 工具调用对象。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiToolCall {

    /**
     * 工具调用 ID。
     */
    private String id;

    /**
     * 流式工具调用序号。
     */
    private Integer index;

    /**
     * 工具类型。
     */
    private String type;

    /**
     * 函数信息。
     */
    private ApiFunction function;
}

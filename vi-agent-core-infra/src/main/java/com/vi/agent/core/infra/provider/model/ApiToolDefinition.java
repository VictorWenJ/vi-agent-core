package com.vi.agent.core.infra.provider.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 工具定义。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiToolDefinition {

    /**
     * 工具类型。
     */
    private String type;

    /**
     * 函数定义。
     */
    private ApiFunction function;
}

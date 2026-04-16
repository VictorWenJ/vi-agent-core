package com.vi.agent.core.infra.provider.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
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

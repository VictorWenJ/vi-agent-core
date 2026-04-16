package com.vi.agent.core.infra.provider.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ApiFunction {

    /**
     * 函数名称。
     */
    private String name;

    /**
     * 函数描述。
     */
    private String description;

    /**
     * 参数 schema。
     */
    private Object parameters;

    /**
     * 参数 JSON 字符串。
     */
    private String arguments;
}

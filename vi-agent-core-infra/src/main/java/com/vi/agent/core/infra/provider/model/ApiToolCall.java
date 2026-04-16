package com.vi.agent.core.infra.provider.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ApiToolCall {

    /**
     * 工具调用 ID。
     */
    private String id;

    /**
     * 调用类型。
     */
    private String type;

    /**
     * 函数信息。
     */
    private ApiFunction function;
}

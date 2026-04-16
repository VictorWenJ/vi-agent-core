package com.vi.agent.core.infra.provider.protocol.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Function 定义或调用信息。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionsFunction {

    /**
     * 函数名称。
     */
    private String name;

    /**
     * 函数描述。
     */
    private String description;

    /**
     * 函数参数 schema。
     */
    private Object parameters;

    /**
     * 函数参数 JSON 字符串。
     */
    private String arguments;
}

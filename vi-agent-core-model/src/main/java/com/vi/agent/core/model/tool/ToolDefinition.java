package com.vi.agent.core.model.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 工具定义。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {

    /** 工具名称。 */
    private String name;

    /** 工具描述。 */
    private String description;

    /** 工具参数 Schema 的 JSON 字符串。 */
    private String parametersJson;
}

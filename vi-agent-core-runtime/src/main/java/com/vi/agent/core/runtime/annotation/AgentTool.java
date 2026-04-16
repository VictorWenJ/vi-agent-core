package com.vi.agent.core.runtime.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Agent 工具标记注解。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AgentTool {

    /**
     * 工具唯一名称。
     *
     * @return 工具名称
     */
    String name();

    /**
     * 工具描述。
     *
     * @return 描述信息
     */
    String description() default "";

    /**
     * 工具参数 Schema(JSON)。
     *
     * @return 参数 schema
     */
    String parametersJson() default "{\"type\":\"object\",\"properties\":{}}";
}

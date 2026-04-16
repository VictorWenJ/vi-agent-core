package com.vi.agent.core.infra.integration.mock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.runtime.annotation.AgentTool;
import com.vi.agent.core.runtime.tool.ToolBundle;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Phase 1 mock 只读工具集合。
 */
@Slf4j
public class MockReadOnlyTools implements ToolBundle {

    /**
     * 获取当前时间。
     *
     * @return ISO 时间字符串
     */
    @AgentTool(
        name = "get_time",
        description = "获取当前系统时间",
        parametersJson = "{\"type\":\"object\",\"properties\":{}}"
    )
    public String getTime() {
        String value = OffsetDateTime.now().toString();
        log.info("MockReadOnlyTools get_time called");
        return value;
    }

    /**
     * 文本回显。
     *
     * @param argumentsJson 参数 JSON
     * @return 回显文本
     */
    @AgentTool(
        name = "echo_text",
        description = "回显输入文本",
        parametersJson = "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},\"required\":[\"text\"]}"
    )
    public String echoText(String argumentsJson) {
        Map<String, Object> args = JsonUtils.jsonToBean(argumentsJson, new TypeReference<Map<String, Object>>() {
        }.getType());
        String text = Optional.ofNullable(args)
            .map(map -> map.get("text"))
            .map(String::valueOf)
            .orElse("");
        log.info("MockReadOnlyTools echo_text called textLength={}", text.length());
        return text;
    }
}

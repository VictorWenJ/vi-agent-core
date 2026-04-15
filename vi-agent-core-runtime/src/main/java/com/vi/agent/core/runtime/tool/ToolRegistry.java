package com.vi.agent.core.runtime.tool;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表。
 */
public class ToolRegistry {

    /** 已注册工具映射。 */
    private final Map<String, ToolExecutor> executors = new ConcurrentHashMap<>();

    /**
     * 注册工具。
     *
     * @param toolName 工具名
     * @param executor 工具执行器
     */
    public void register(String toolName, ToolExecutor executor) {
        executors.put(toolName, executor);
    }

    /**
     * 查询工具执行器。
     *
     * @param toolName 工具名
     * @return 可选执行器
     */
    public Optional<ToolExecutor> find(String toolName) {
        return Optional.ofNullable(executors.get(toolName));
    }
}

package com.vi.agent.core.runtime.memory;

/**
 * 记忆服务接口（Phase 1 仅预留）。
 */
public interface MemoryService {

    /**
     * 更新记忆内容。
     *
     * @param key 记忆键
     * @param value 记忆值
     */
    void update(String key, String value);

    /**
     * 查询记忆内容。
     *
     * @param key 记忆键
     * @return 记忆值
     */
    String get(String key);
}

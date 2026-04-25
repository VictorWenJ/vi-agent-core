package com.vi.agent.core.model.memory;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Memory 写入模式。
 */
@Getter
@AllArgsConstructor
public enum MemoryWriteMode {

    HOT_PATH_SYNC("hot_path_sync", "主链路同步写"),

    AFTER_COMMIT_SYNC("after_commit_sync", "事务提交后同步写"),

    BACKGROUND_ASYNC("background_async", "后台异步写"),

    DEGRADED_SKIP("degraded_skip", "降级跳过");

    private final String value;

    private final String desc;
}

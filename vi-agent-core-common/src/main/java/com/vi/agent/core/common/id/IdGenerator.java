package com.vi.agent.core.common.id;

/**
 * 通用 ID 生成器接口。
 */
public interface IdGenerator {

    /**
     * 生成唯一 ID。
     *
     * @return 唯一 ID 字符串
     */
    String nextId();
}

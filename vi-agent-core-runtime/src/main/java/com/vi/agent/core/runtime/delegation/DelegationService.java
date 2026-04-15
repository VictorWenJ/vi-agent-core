package com.vi.agent.core.runtime.delegation;

/**
 * 委派服务接口（Phase 1 仅预留）。
 */
public interface DelegationService {

    /**
     * 委派子任务。
     *
     * @param taskDescription 子任务描述
     * @return 子任务执行摘要
     */
    String delegate(String taskDescription);
}

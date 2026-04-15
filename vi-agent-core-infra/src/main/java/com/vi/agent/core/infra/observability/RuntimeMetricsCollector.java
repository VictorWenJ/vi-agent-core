package com.vi.agent.core.infra.observability;

/**
 * Runtime 指标采集器。
 */
public interface RuntimeMetricsCollector {

    /**
     * 记录成功指标。
     *
     * @param traceId 链路追踪 ID
     * @param runId 运行 ID
     * @param durationMs 耗时毫秒
     */
    void recordSuccess(String traceId, String runId, long durationMs);

    /**
     * 记录失败指标。
     *
     * @param traceId 链路追踪 ID
     * @param runId 运行 ID
     * @param durationMs 耗时毫秒
     */
    void recordFailure(String traceId, String runId, long durationMs);
}

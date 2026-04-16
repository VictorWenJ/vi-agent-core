package com.vi.agent.core.infra.observability;

/**
 * 空实现指标采集器。
 */
public class NoopRuntimeMetricsCollector implements RuntimeMetricsCollector {

    @Override
    public void recordSuccess(String traceId, String runId, long durationMs) {
        // Phase 1 最小占位实现
    }

    @Override
    public void recordFailure(String traceId, String runId, long durationMs) {
        // Phase 1 最小占位实现
    }
}

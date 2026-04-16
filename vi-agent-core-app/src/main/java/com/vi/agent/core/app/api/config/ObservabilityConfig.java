package com.vi.agent.core.app.api.config;

import com.vi.agent.core.infra.observability.NoopRuntimeMetricsCollector;
import com.vi.agent.core.infra.observability.RuntimeMetricsCollector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ObservabilityConfig {

    @Bean
    public RuntimeMetricsCollector runtimeMetricsCollector() {
        return new NoopRuntimeMetricsCollector();
    }
}
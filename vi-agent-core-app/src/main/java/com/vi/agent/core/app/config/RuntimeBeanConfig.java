package com.vi.agent.core.app.config;

import com.vi.agent.core.common.id.RunIdGenerator;
import com.vi.agent.core.common.id.TraceIdGenerator;
import com.vi.agent.core.infra.observability.NoopRuntimeMetricsCollector;
import com.vi.agent.core.infra.observability.RuntimeMetricsCollector;
import com.vi.agent.core.infra.persistence.InMemoryTranscriptRepository;
import com.vi.agent.core.infra.persistence.TranscriptRepository;
import com.vi.agent.core.infra.persistence.TranscriptStoreService;
import com.vi.agent.core.infra.provider.LlmProvider;
import com.vi.agent.core.infra.provider.OpenAiProvider;
import com.vi.agent.core.model.tool.ToolResult;
import com.vi.agent.core.runtime.context.ContextAssembler;
import com.vi.agent.core.runtime.context.SimpleContextAssembler;
import com.vi.agent.core.runtime.engine.AgentLoopEngine;
import com.vi.agent.core.runtime.engine.DefaultAgentLoopEngine;
import com.vi.agent.core.runtime.orchestrator.RuntimeOrchestrator;
import com.vi.agent.core.runtime.port.TranscriptStore;
import com.vi.agent.core.runtime.tool.DefaultToolGateway;
import com.vi.agent.core.runtime.tool.ToolGateway;
import com.vi.agent.core.runtime.tool.ToolRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.OffsetDateTime;

/**
 * Runtime 与 Infra 装配配置。
 */
@Configuration
public class RuntimeBeanConfig {

    @Bean
    public TraceIdGenerator traceIdGenerator() {
        return new TraceIdGenerator();
    }

    @Bean
    public RunIdGenerator runIdGenerator() {
        return new RunIdGenerator();
    }

    @Bean
    public ToolRegistry toolRegistry() {
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register("get_time", toolCall -> new ToolResult(
            toolCall.getToolCallId(),
            toolCall.getToolName(),
            true,
            OffsetDateTime.now().toString()
        ));
        return toolRegistry;
    }

    @Bean
    public ToolGateway toolGateway(ToolRegistry toolRegistry) {
        return new DefaultToolGateway(toolRegistry);
    }

    @Bean
    public ContextAssembler contextAssembler() {
        return new SimpleContextAssembler();
    }

    @Bean
    public LlmProvider llmProvider() {
        return new OpenAiProvider();
    }

    @Bean
    public AgentLoopEngine agentLoopEngine(LlmProvider llmProvider) {
        return new DefaultAgentLoopEngine(llmProvider);
    }

    @Bean
    public TranscriptRepository transcriptRepository() {
        return new InMemoryTranscriptRepository();
    }

    @Bean
    public TranscriptStore transcriptStore(TranscriptRepository transcriptRepository) {
        return new TranscriptStoreService(transcriptRepository);
    }

    @Bean
    public RuntimeMetricsCollector runtimeMetricsCollector() {
        return new NoopRuntimeMetricsCollector();
    }

    @Bean
    public RuntimeOrchestrator runtimeOrchestrator(
        ContextAssembler contextAssembler,
        AgentLoopEngine agentLoopEngine,
        ToolGateway toolGateway,
        TranscriptStore transcriptStore,
        TraceIdGenerator traceIdGenerator,
        RunIdGenerator runIdGenerator
    ) {
        return new RuntimeOrchestrator(
            contextAssembler,
            agentLoopEngine,
            toolGateway,
            transcriptStore,
            traceIdGenerator,
            runIdGenerator
        );
    }
}

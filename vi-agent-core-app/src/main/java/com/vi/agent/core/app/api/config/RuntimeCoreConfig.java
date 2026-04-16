package com.vi.agent.core.app.api.config;

import com.vi.agent.core.app.api.config.properties.RuntimeProperties;
import com.vi.agent.core.common.id.ConversationIdGenerator;
import com.vi.agent.core.common.id.MessageIdGenerator;
import com.vi.agent.core.common.id.RunIdGenerator;
import com.vi.agent.core.common.id.ToolCallIdGenerator;
import com.vi.agent.core.common.id.TraceIdGenerator;
import com.vi.agent.core.common.id.TurnIdGenerator;
import com.vi.agent.core.infra.provider.LlmProvider;
import com.vi.agent.core.runtime.context.ContextAssembler;
import com.vi.agent.core.runtime.context.SimpleContextAssembler;
import com.vi.agent.core.runtime.engine.AgentLoopEngine;
import com.vi.agent.core.runtime.engine.SimpleAgentLoopEngine;
import com.vi.agent.core.runtime.orchestrator.RuntimeOrchestrator;
import com.vi.agent.core.runtime.port.TranscriptStore;
import com.vi.agent.core.runtime.tool.ToolGateway;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RuntimeCoreConfig {

    @Bean
    @ConfigurationProperties(prefix = "vi.agent.runtime")
    public RuntimeProperties runtimeProperties() {
        return new RuntimeProperties();
    }

    @Bean
    public ContextAssembler contextAssembler() {
        return new SimpleContextAssembler();
    }

    @Bean
    public AgentLoopEngine agentLoopEngine(LlmProvider llmProvider) {
        return new SimpleAgentLoopEngine(llmProvider);
    }

    @Bean
    public RuntimeOrchestrator runtimeOrchestrator(
        ContextAssembler contextAssembler,
        AgentLoopEngine agentLoopEngine,
        ToolGateway toolGateway,
        TranscriptStore transcriptStore,
        TraceIdGenerator traceIdGenerator,
        RunIdGenerator runIdGenerator,
        ConversationIdGenerator conversationIdGenerator,
        TurnIdGenerator turnIdGenerator,
        MessageIdGenerator messageIdGenerator,
        ToolCallIdGenerator toolCallIdGenerator,
        RuntimeProperties runtimeProperties
    ) {
        return new RuntimeOrchestrator(
            contextAssembler,
            agentLoopEngine,
            toolGateway,
            transcriptStore,
            traceIdGenerator,
            runIdGenerator,
            conversationIdGenerator,
            turnIdGenerator,
            messageIdGenerator,
            toolCallIdGenerator,
            runtimeProperties.getMaxIterations()
        );
    }
}
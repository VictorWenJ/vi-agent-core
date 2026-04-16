package com.vi.agent.core.app.config;

import com.vi.agent.core.common.id.ConversationIdGenerator;
import com.vi.agent.core.common.id.MessageIdGenerator;
import com.vi.agent.core.common.id.RunIdGenerator;
import com.vi.agent.core.common.id.ToolCallIdGenerator;
import com.vi.agent.core.common.id.TraceIdGenerator;
import com.vi.agent.core.common.id.TurnIdGenerator;
import com.vi.agent.core.infra.integration.mock.MockReadOnlyTools;
import com.vi.agent.core.infra.observability.NoopRuntimeMetricsCollector;
import com.vi.agent.core.infra.observability.RuntimeMetricsCollector;
import com.vi.agent.core.infra.persistence.RedisTranscriptRepository;
import com.vi.agent.core.infra.persistence.TranscriptRedisMapper;
import com.vi.agent.core.infra.persistence.TranscriptRepository;
import com.vi.agent.core.infra.persistence.TranscriptStoreService;
import com.vi.agent.core.infra.persistence.config.RedisTranscriptProperties;
import com.vi.agent.core.infra.provider.DeepSeekProvider;
import com.vi.agent.core.infra.provider.LlmProvider;
import com.vi.agent.core.infra.provider.common.JdkLlmHttpExecutor;
import com.vi.agent.core.infra.provider.common.LlmHttpExecutor;
import com.vi.agent.core.infra.provider.config.DeepSeekProperties;
import com.vi.agent.core.infra.provider.config.DoubaoProperties;
import com.vi.agent.core.infra.provider.config.OpenAiProperties;
import com.vi.agent.core.runtime.context.ContextAssembler;
import com.vi.agent.core.runtime.context.SimpleContextAssembler;
import com.vi.agent.core.runtime.engine.AgentLoopEngine;
import com.vi.agent.core.runtime.engine.DefaultAgentLoopEngine;
import com.vi.agent.core.runtime.engine.StreamAgentLoopEngine;
import com.vi.agent.core.runtime.orchestrator.RuntimeOrchestrator;
import com.vi.agent.core.runtime.port.TranscriptStore;
import com.vi.agent.core.runtime.tool.DefaultToolGateway;
import com.vi.agent.core.runtime.tool.ToolBundle;
import com.vi.agent.core.runtime.tool.ToolGateway;
import com.vi.agent.core.runtime.tool.ToolRegistry;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

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
    public ConversationIdGenerator conversationIdGenerator() {
        return new ConversationIdGenerator();
    }

    @Bean
    public TurnIdGenerator turnIdGenerator() {
        return new TurnIdGenerator();
    }

    @Bean
    public MessageIdGenerator messageIdGenerator() {
        return new MessageIdGenerator();
    }

    @Bean
    public ToolCallIdGenerator toolCallIdGenerator() {
        return new ToolCallIdGenerator();
    }

    @Bean
    @ConfigurationProperties(prefix = "vi.agent.provider.deepseek")
    public DeepSeekProperties deepSeekProperties() {
        return new DeepSeekProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "vi.agent.provider.doubao")
    public DoubaoProperties doubaoProperties() {
        return new DoubaoProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "vi.agent.provider.openai")
    public OpenAiProperties openAiProperties() {
        return new OpenAiProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "vi.agent.transcript.redis")
    public RedisTranscriptProperties redisTranscriptProperties() {
        return new RedisTranscriptProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "vi.agent.runtime")
    public RuntimeProperties runtimeProperties() {
        return new RuntimeProperties();
    }

    @Bean
    public LlmHttpExecutor llmHttpExecutor() {
        return new JdkLlmHttpExecutor();
    }

    @Bean
    public LlmProviderFactory llmProviderFactory(
        DeepSeekProperties deepSeekProperties,
        DoubaoProperties doubaoProperties,
        OpenAiProperties openAiProperties,
        LlmHttpExecutor llmHttpExecutor
    ) {
        return new LlmProviderFactory(
            deepSeekProperties,
            doubaoProperties,
            openAiProperties,
            llmHttpExecutor
        );
    }

    @Bean
    public ToolBundle mockReadOnlyTools() {
        return new MockReadOnlyTools();
    }

    @Bean
    public ToolRegistry toolRegistry(List<ToolBundle> toolBundles) {
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.registerAnnotatedTools(toolBundles);
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
    public LlmProvider llmProvider(
        LlmProviderFactory llmProviderFactory,
        ProviderRoutingProperties providerRoutingProperties
    ) {
        return llmProviderFactory.create(providerRoutingProperties.getDefaultProvider());
    }

    @Bean
    public AgentLoopEngine agentLoopEngine(LlmProvider llmProvider) {
        return new DefaultAgentLoopEngine(llmProvider);
    }

    @Bean
    public TranscriptRepository transcriptRepository(
        StringRedisTemplate stringRedisTemplate,
        RedisTranscriptProperties redisTranscriptProperties
    ) {
        return new RedisTranscriptRepository(stringRedisTemplate, redisTranscriptProperties);
    }

    @Bean
    public TranscriptRedisMapper transcriptRedisMapper() {
        return new TranscriptRedisMapper();
    }

    @Bean
    public TranscriptStore transcriptStore(
        TranscriptRepository transcriptRepository,
        TranscriptRedisMapper transcriptRedisMapper
    ) {
        return new TranscriptStoreService(transcriptRepository, transcriptRedisMapper);
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

package com.vi.agent.core.app.config;

import com.vi.agent.core.common.id.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IdGeneratorConfig {

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
    public SessionIdGenerator sessionIdGenerator() {
        return new SessionIdGenerator();
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
    public WorkingContextSnapshotIdGenerator workingContextSnapshotIdGenerator() {
        return new WorkingContextSnapshotIdGenerator();
    }
}

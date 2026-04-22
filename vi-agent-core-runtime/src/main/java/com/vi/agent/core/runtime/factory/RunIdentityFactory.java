package com.vi.agent.core.runtime.factory;

import com.vi.agent.core.common.id.*;
import com.vi.agent.core.model.runtime.RunMetadata;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Unified identity factory for run/session/message ids.
 */
@Component
public class RunIdentityFactory {

    @Resource
    private TraceIdGenerator traceIdGenerator;

    @Resource
    private RunIdGenerator runIdGenerator;

    @Resource
    private TurnIdGenerator turnIdGenerator;

    @Resource
    private MessageIdGenerator messageIdGenerator;

    @Resource
    private ToolCallIdGenerator toolCallIdGenerator;

    @Resource
    private ConversationIdGenerator conversationIdGenerator;

    @Resource
    private SessionIdGenerator sessionIdGenerator;

    public RunMetadata createRunMetadata() {
        return RunMetadata.builder()
            .traceId(traceIdGenerator.nextId())
            .runId(runIdGenerator.nextId())
            .turnId(turnIdGenerator.nextId())
            .build();
    }

    public String nextMessageId() {
        return messageIdGenerator.nextId();
    }

    public String nextToolCallId() {
        return toolCallIdGenerator.nextId();
    }

    public String nextToolCallRecordId() {
        return "tcr-" + UUID.randomUUID();
    }

    public String nextToolExecutionId() {
        return "tex-" + UUID.randomUUID();
    }

    public String nextRunEventId() {
        return "evt-" + UUID.randomUUID();
    }

    public String nextConversationId() {
        return conversationIdGenerator.nextId();
    }

    public String nextSessionId() {
        return sessionIdGenerator.nextId();
    }
}

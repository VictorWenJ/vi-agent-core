package com.vi.agent.core.runtime.factory;

import com.vi.agent.core.common.id.*;
import com.vi.agent.core.model.runtime.RunMetadata;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

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

    @Resource
    private ToolCallRecordIdGenerator toolCallRecordIdGenerator;

    @Resource
    private ToolExecutionIdGenerator toolExecutionIdGenerator;

    @Resource
    private RunEventIdGenerator runEventIdGenerator;

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
        return toolCallRecordIdGenerator.nextId();
    }

    public String nextToolExecutionId() {
        return toolExecutionIdGenerator.nextId();
    }

    public String nextRunEventId() {
        return runEventIdGenerator.nextId();
    }

    public String nextConversationId() {
        return conversationIdGenerator.nextId();
    }

    public String nextSessionId() {
        return sessionIdGenerator.nextId();
    }
}

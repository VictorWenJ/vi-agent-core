package com.vi.agent.core.runtime.memory.evidence;

import com.vi.agent.core.model.memory.EvidenceSource;
import com.vi.agent.core.model.memory.EvidenceSourceType;
import com.vi.agent.core.model.memory.ToolOutcomeDigest;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.ToolMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Evidence 来源对象工厂。
 */
@Component
public class EvidenceSourceFactory {

    private static final int EXCERPT_MAX_LENGTH = 512;

    public Optional<EvidenceSource> fromMessage(Message message, String workingContextSnapshotId, String internalTaskId) {
        if (message == null || message.getRole() == MessageRole.SYSTEM || message.getRole() == MessageRole.SUMMARY) {
            return Optional.empty();
        }
        EvidenceSourceType sourceType = switch (message.getRole()) {
            case USER -> EvidenceSourceType.USER_MESSAGE;
            case ASSISTANT -> EvidenceSourceType.ASSISTANT_MESSAGE;
            case TOOL -> EvidenceSourceType.TOOL_RESULT;
            default -> null;
        };
        if (sourceType == null) {
            return Optional.empty();
        }
        return Optional.of(EvidenceSource.builder()
            .sourceType(sourceType)
            .sessionId(message.getSessionId())
            .turnId(message.getTurnId())
            .runId(message.getRunId())
            .messageId(message.getMessageId())
            .toolCallRecordId(message instanceof ToolMessage toolMessage ? toolMessage.getToolCallRecordId() : null)
            .workingContextSnapshotId(workingContextSnapshotId)
            .internalTaskId(internalTaskId)
            .excerptText(excerpt(message.getContentText()))
            .build());
    }

    public Optional<EvidenceSource> fromToolOutcome(
        ToolOutcomeDigest digest,
        String sessionId,
        String turnId,
        String runId,
        String workingContextSnapshotId,
        String internalTaskId
    ) {
        if (digest == null || (StringUtils.isBlank(digest.getToolCallRecordId()) && StringUtils.isBlank(digest.getToolExecutionId()))) {
            return Optional.empty();
        }
        return Optional.of(EvidenceSource.builder()
            .sourceType(EvidenceSourceType.TOOL_RESULT)
            .sessionId(sessionId)
            .turnId(turnId)
            .runId(runId)
            .toolCallRecordId(digest.getToolCallRecordId())
            .workingContextSnapshotId(workingContextSnapshotId)
            .internalTaskId(internalTaskId)
            .excerptText(excerpt(digest.getSummary()))
            .build());
    }

    public EvidenceSource fromWorkingContextSnapshot(String sessionId, String turnId, String runId, String workingContextSnapshotId, String excerptText) {
        return EvidenceSource.builder()
            .sourceType(EvidenceSourceType.WORKING_CONTEXT_SNAPSHOT)
            .sessionId(sessionId)
            .turnId(turnId)
            .runId(runId)
            .workingContextSnapshotId(workingContextSnapshotId)
            .excerptText(excerpt(excerptText))
            .build();
    }

    public EvidenceSource fromInternalTask(String sessionId, String turnId, String runId, String internalTaskId, String excerptText) {
        return EvidenceSource.builder()
            .sourceType(EvidenceSourceType.INTERNAL_TASK)
            .sessionId(sessionId)
            .turnId(turnId)
            .runId(runId)
            .internalTaskId(internalTaskId)
            .excerptText(excerpt(excerptText))
            .build();
    }

    private String excerpt(String text) {
        return StringUtils.abbreviate(text, EXCERPT_MAX_LENGTH);
    }
}

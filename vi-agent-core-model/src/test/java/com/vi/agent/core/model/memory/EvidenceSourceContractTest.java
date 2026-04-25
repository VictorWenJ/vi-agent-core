package com.vi.agent.core.model.memory;

import com.vi.agent.core.common.util.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvidenceSourceContractTest {

    @Test
    void shouldKeepSourceIdentityAndExcerpt() {
        EvidenceSource source = EvidenceSource.builder()
            .sourceType(EvidenceSourceType.USER_MESSAGE)
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .messageId("msg-1")
            .excerptText("用户明确要求")
            .build();

        assertEquals("sess-1", source.getSessionId());
        assertEquals("msg-1", source.getMessageId());
        assertEquals("用户明确要求", source.getExcerptText());
    }

    @Test
    void shouldSerializeAndDeserializeSourceFields() {
        EvidenceSource source = EvidenceSource.builder()
            .sourceType(EvidenceSourceType.TOOL_RESULT)
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .toolCallRecordId("tcr-1")
            .workingContextSnapshotId("wctx-1")
            .internalTaskId("it-1")
            .excerptText("工具摘要")
            .build();

        EvidenceSource restored = JsonUtils.jsonToBean(JsonUtils.toJson(source), EvidenceSource.class);

        assertEquals(EvidenceSourceType.TOOL_RESULT, restored.getSourceType());
        assertEquals("tcr-1", restored.getToolCallRecordId());
        assertEquals("工具摘要", restored.getExcerptText());
    }

    @Test
    void shouldSupportWorkingContextSnapshotSourceType() {
        EvidenceSource source = EvidenceSource.builder()
            .sourceType(EvidenceSourceType.WORKING_CONTEXT_SNAPSHOT)
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .workingContextSnapshotId("wctx-1")
            .excerptText("context snapshot")
            .build();

        assertEquals(EvidenceSourceType.WORKING_CONTEXT_SNAPSHOT, source.getSourceType());
        assertEquals("wctx-1", source.getWorkingContextSnapshotId());
    }
}

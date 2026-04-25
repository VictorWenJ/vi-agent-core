package com.vi.agent.core.model.memory;

import com.vi.agent.core.common.util.JsonUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationSummaryTest {

    @Test
    void shouldKeepCoverageAndGeneratorMetadata() {
        ConversationSummary summary = ConversationSummary.builder()
            .summaryId("sum-1")
            .sessionId("sess-1")
            .summaryVersion(2L)
            .coveredFromSequenceNo(1L)
            .coveredToSequenceNo(10L)
            .summaryText("历史摘要")
            .summaryTemplateKey("summary_extract")
            .summaryTemplateVersion("v1")
            .generatorProvider("deepseek")
            .generatorModel("deepseek-chat")
            .createdAt(Instant.parse("2026-04-25T00:00:00Z"))
            .build();

        assertEquals(1L, summary.getCoveredFromSequenceNo());
        assertEquals(10L, summary.getCoveredToSequenceNo());
        assertEquals("deepseek-chat", summary.getGeneratorModel());
    }

    @Test
    void shouldSerializeAndDeserializeSummary() {
        ConversationSummary summary = ConversationSummary.builder()
            .summaryId("sum-1")
            .sessionId("sess-1")
            .summaryVersion(2L)
            .coveredFromSequenceNo(1L)
            .coveredToSequenceNo(10L)
            .summaryText("历史摘要")
            .createdAt(Instant.parse("2026-04-25T00:00:00Z"))
            .build();

        ConversationSummary restored = JsonUtils.jsonToBean(JsonUtils.toJson(summary), ConversationSummary.class);

        assertEquals("sum-1", restored.getSummaryId());
        assertEquals("历史摘要", restored.getSummaryText());
        assertEquals(10L, restored.getCoveredToSequenceNo());
    }
}

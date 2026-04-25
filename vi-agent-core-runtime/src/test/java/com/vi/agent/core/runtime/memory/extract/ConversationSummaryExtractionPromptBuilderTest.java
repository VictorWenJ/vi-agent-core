package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationSummaryExtractionPromptBuilderTest {

    private final ConversationSummaryExtractionPromptBuilder builder = new ConversationSummaryExtractionPromptBuilder();

    @Test
    void buildPromptShouldContainLatestSummaryCompletedTurnMessagesAndAllowedSchema() {
        String prompt = builder.buildPrompt(command());

        assertTrue(prompt.contains("Previous conversation summary"));
        assertTrue(prompt.contains("existing durable summary"));
        assertTrue(prompt.contains("Completed raw turn transcript"));
        assertTrue(prompt.contains("user asked for D3 summary"));
        assertTrue(prompt.contains("assistant answered D3 summary"));
        assertTrue(prompt.contains("Latest session state reference"));
        assertTrue(prompt.contains("state-version: 7"));
        assertTrue(prompt.contains("summaryText"));
        assertTrue(prompt.contains("skipped"));
    }

    @Test
    void buildPromptShouldRejectUserVisibleReplyStateDeltaEvidenceAndProjectionSources() {
        String prompt = builder.buildPrompt(command());

        assertTrue(prompt.contains("Do not generate a user-visible reply"));
        assertTrue(prompt.contains("Do not generate StateDelta"));
        assertTrue(prompt.contains("Do not generate evidence"));
        assertTrue(prompt.contains("strict JSON"));
        assertFalse(prompt.contains("WorkingContextProjection"));
        assertFalse(prompt.contains("workingMessages"));
        assertFalse(prompt.contains("synthetic"));
    }

    @Test
    void buildPromptShouldForbidSystemFieldsAndUnexpectedSchemas() {
        String prompt = builder.buildPrompt(command());

        assertTrue(prompt.contains("summaryId"));
        assertTrue(prompt.contains("summaryVersion"));
        assertTrue(prompt.contains("coveredFromSequenceNo"));
        assertTrue(prompt.contains("coveredToSequenceNo"));
        assertTrue(prompt.contains("stateDelta"));
        assertTrue(prompt.contains("evidence"));
        assertTrue(prompt.contains("upsert"));
        assertTrue(prompt.contains("remove"));
    }

    private ConversationSummaryExtractionCommand command() {
        ConversationSummary latestSummary = ConversationSummary.builder()
            .summaryId("summary-1")
            .sessionId("session-1")
            .summaryVersion(6L)
            .coveredFromSequenceNo(1L)
            .coveredToSequenceNo(10L)
            .summaryText("existing durable summary")
            .summaryTemplateKey("summary_extract_inline")
            .summaryTemplateVersion("p2-d-2-v1")
            .generatorProvider("fake")
            .generatorModel("fake-model")
            .createdAt(Instant.parse("2026-04-26T00:00:00Z"))
            .build();
        SessionStateSnapshot latestState = SessionStateSnapshot.builder()
            .snapshotId("state-1")
            .sessionId("session-1")
            .stateVersion(7L)
            .taskGoal("keep the project aligned with P2-D")
            .updatedAt(Instant.parse("2026-04-26T00:01:00Z"))
            .build();
        List<Message> turnMessages = List.of(
            UserMessage.create("msg-user", "conversation-1", "session-1", "turn-1", "run-1", 11L, "user asked for D3 summary"),
            AssistantMessage.create("msg-assistant", "conversation-1", "session-1", "turn-1", "run-1", 12L, "assistant answered D3 summary", List.of(), null, null)
        );

        return ConversationSummaryExtractionCommand.builder()
            .conversationId("conversation-1")
            .sessionId("session-1")
            .turnId("turn-1")
            .runId("run-1")
            .traceId("trace-1")
            .agentMode(AgentMode.GENERAL)
            .latestSummary(latestSummary)
            .latestState(latestState)
            .turnMessages(turnMessages)
            .workingContextSnapshotId("wctx-1")
            .build();
    }
}

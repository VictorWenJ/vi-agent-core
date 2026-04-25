package com.vi.agent.core.runtime.memory.evidence;

import com.vi.agent.core.common.id.EvidenceIdGenerator;
import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.memory.AnswerStyle;
import com.vi.agent.core.model.memory.ConfirmedFactRecord;
import com.vi.agent.core.model.memory.ConstraintRecord;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.DecisionRecord;
import com.vi.agent.core.model.memory.EvidenceRef;
import com.vi.agent.core.model.memory.EvidenceTargetType;
import com.vi.agent.core.model.memory.OpenLoop;
import com.vi.agent.core.model.memory.OpenLoopKind;
import com.vi.agent.core.model.memory.OpenLoopStatus;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.memory.StateDelta;
import com.vi.agent.core.model.memory.ToolOutcomeDigest;
import com.vi.agent.core.model.memory.ToolOutcomeFreshnessPolicy;
import com.vi.agent.core.model.memory.statepatch.PhaseStatePatch;
import com.vi.agent.core.model.memory.statepatch.UserPreferencePatch;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.MemoryEvidenceRepository;
import com.vi.agent.core.runtime.support.TestFieldUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryEvidenceBinderTest {

    @Test
    void bindShouldSaveEvidenceForWrittenStateAndSummaryItems() {
        Fixture fixture = Fixture.create();

        EvidenceBindingResult result = fixture.binder.bind(fullCommand());

        assertTrue(result.isSuccess());
        assertFalse(result.isDegraded());
        assertEquals(9, result.getSavedCount());
        assertEquals(9, fixture.repository.saved.size());
        assertEquals(List.of("evd-1", "evd-2", "evd-3", "evd-4", "evd-5", "evd-6", "evd-7", "evd-8", "evd-9"), result.getEvidenceIds());
        assertTrue(fixture.repository.saved.stream().anyMatch(evidence -> evidence.getTarget().getTargetField().equals("confirmedFacts")));
        assertTrue(fixture.repository.saved.stream().anyMatch(evidence -> evidence.getTarget().getTargetField().equals("constraints")));
        assertTrue(fixture.repository.saved.stream().anyMatch(evidence -> evidence.getTarget().getTargetField().equals("decisions")));
        assertTrue(fixture.repository.saved.stream().anyMatch(evidence -> evidence.getTarget().getTargetType() == EvidenceTargetType.OPEN_LOOP));
        assertTrue(fixture.repository.saved.stream().anyMatch(evidence -> evidence.getTarget().getTargetType() == EvidenceTargetType.TOOL_OUTCOME_DIGEST));
        assertTrue(fixture.repository.saved.stream().anyMatch(evidence -> evidence.getTarget().getTargetField().equals("userPreferences.answerStyle")));
        assertTrue(fixture.repository.saved.stream().anyMatch(evidence -> evidence.getTarget().getTargetField().equals("phaseState.summaryEnabled")));
        assertTrue(fixture.repository.saved.stream().anyMatch(evidence -> evidence.getTarget().getTargetType() == EvidenceTargetType.SUMMARY_SEGMENT));
    }

    @Test
    void bindShouldSkipWhenDeltaEmptyAndSummaryMissing() {
        Fixture fixture = Fixture.create();

        EvidenceBindingResult result = fixture.binder.bind(EvidenceBindingCommand.builder()
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .traceId("trace-1")
            .stateDelta(StateDelta.builder().build())
            .turnMessages(List.of(message("msg-user-1", "fact source")))
            .sourceCandidateIds(List.of("msg-user-1"))
            .agentMode(AgentMode.GENERAL)
            .build());

        assertTrue(result.isSkipped());
        assertEquals(0, result.getSavedCount());
        assertTrue(fixture.repository.saved.isEmpty());
    }

    @Test
    void bindShouldFallbackToCompletedTurnMessageWhenSourceCandidateInvalid() {
        Fixture fixture = Fixture.create();
        EvidenceBindingCommand command = EvidenceBindingCommand.builder()
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .traceId("trace-1")
            .stateTaskId("task-state")
            .newState(newState())
            .stateDelta(StateDelta.builder()
                .confirmedFactAppend(ConfirmedFactRecord.builder().factId("fact-1").content("fact").build())
                .sourceCandidateId("missing-msg")
                .build())
            .turnMessage(message("msg-user-1", "fallback source"))
            .sourceCandidateId("missing-msg")
            .agentMode(AgentMode.GENERAL)
            .build();

        EvidenceBindingResult result = fixture.binder.bind(command);

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertEquals(1, result.getSavedCount());
        assertEquals("msg-user-1", fixture.repository.saved.get(0).getSource().getMessageId());
        assertTrue(result.getFailureReason().contains("fallback"));
    }

    @Test
    void bindShouldNotCreateFakeEvidenceWhenNoTrustedSourceExists() {
        Fixture fixture = Fixture.create();
        EvidenceBindingCommand command = EvidenceBindingCommand.builder()
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .traceId("trace-1")
            .newState(newState())
            .stateDelta(StateDelta.builder()
                .confirmedFactAppend(ConfirmedFactRecord.builder().factId("fact-1").content("fact").build())
                .build())
            .build();

        EvidenceBindingResult result = fixture.binder.bind(command);

        assertTrue(result.isSkipped());
        assertEquals(0, result.getSavedCount());
        assertTrue(fixture.repository.saved.isEmpty());
    }

    @Test
    void bindShouldReturnDegradedWhenRepositorySaveFails() {
        Fixture fixture = Fixture.create();
        fixture.repository.throwOnSaveAll = true;

        EvidenceBindingResult result = fixture.binder.bind(fullCommand());

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertEquals(0, result.getSavedCount());
        assertTrue(result.getFailureReason().contains("evidence save failed"));
    }

    private static EvidenceBindingCommand fullCommand() {
        StateDelta delta = StateDelta.builder()
            .confirmedFactAppend(ConfirmedFactRecord.builder().factId("fact-1").content("fact").build())
            .constraintAppend(ConstraintRecord.builder().constraintId("constraint-1").content("constraint").build())
            .decisionAppend(DecisionRecord.builder().decisionId("decision-1").content("decision").build())
            .openLoopAppend(OpenLoop.builder().loopId("loop-1").kind(OpenLoopKind.USER_INPUT_REQUIRED).content("open loop").status(OpenLoopStatus.OPEN).build())
            .openLoopIdToClose("loop-old")
            .recentToolOutcomeAppend(ToolOutcomeDigest.builder()
                .digestId("digest-1")
                .toolCallRecordId("tcr-1")
                .toolExecutionId("tex-1")
                .toolName("search")
                .summary("tool summary")
                .freshnessPolicy(ToolOutcomeFreshnessPolicy.SESSION)
                .build())
            .userPreferencesPatch(UserPreferencePatch.builder().answerStyle(AnswerStyle.DIRECT).build())
            .phaseStatePatch(PhaseStatePatch.builder().summaryEnabled(true).build())
            .sourceCandidateId("msg-user-1")
            .build();
        return EvidenceBindingCommand.builder()
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .traceId("trace-1")
            .workingContextSnapshotId("wctx-1")
            .stateTaskId("task-state")
            .summaryTaskId("task-summary")
            .newState(newState())
            .stateDelta(delta)
            .newSummary(newSummary())
            .turnMessage(message("msg-user-1", "fact source"))
            .sourceCandidateId("msg-user-1")
            .agentMode(AgentMode.GENERAL)
            .build();
    }

    private static SessionStateSnapshot newState() {
        return SessionStateSnapshot.builder()
            .snapshotId("state-2")
            .sessionId("sess-1")
            .stateVersion(2L)
            .updatedAt(Instant.parse("2026-04-26T00:00:00Z"))
            .build();
    }

    private static ConversationSummary newSummary() {
        return ConversationSummary.builder()
            .summaryId("summary-2")
            .sessionId("sess-1")
            .summaryVersion(2L)
            .summaryText("new summary")
            .createdAt(Instant.parse("2026-04-26T00:00:00Z"))
            .build();
    }

    private static Message message(String messageId, String content) {
        return UserMessage.create(messageId, "conv-1", "sess-1", "turn-1", "run-1", 1L, content);
    }

    private static final class Fixture {
        private final MemoryEvidenceBinder binder = new MemoryEvidenceBinder();
        private final RecordingEvidenceRepository repository = new RecordingEvidenceRepository();

        private static Fixture create() {
            Fixture fixture = new Fixture();
            TestFieldUtils.setField(fixture.binder, "memoryEvidenceRepository", fixture.repository);
            TestFieldUtils.setField(fixture.binder, "evidenceIdGenerator", new FixedEvidenceIdGenerator());
            TestFieldUtils.setField(fixture.binder, "targetFactory", new EvidenceTargetFactory());
            TestFieldUtils.setField(fixture.binder, "sourceFactory", new EvidenceSourceFactory());
            return fixture;
        }
    }

    private static final class FixedEvidenceIdGenerator extends EvidenceIdGenerator {
        private int count;

        @Override
        public String nextId() {
            count++;
            return "evd-" + count;
        }
    }

    private static final class RecordingEvidenceRepository implements MemoryEvidenceRepository {
        private final List<EvidenceRef> saved = new ArrayList<>();
        private boolean throwOnSaveAll;

        @Override
        public void save(EvidenceRef evidenceRef) {
            saved.add(evidenceRef);
        }

        @Override
        public void saveAll(List<EvidenceRef> evidenceRefs) {
            if (throwOnSaveAll) {
                throw new IllegalStateException("evidence save failed");
            }
            saved.addAll(evidenceRefs);
        }

        @Override
        public Optional<EvidenceRef> findByEvidenceId(String evidenceId) {
            return Optional.empty();
        }

        @Override
        public List<EvidenceRef> listBySessionId(String sessionId) {
            return List.of();
        }

        @Override
        public List<EvidenceRef> listByTarget(EvidenceTargetType targetType, String targetRef) {
            return List.of();
        }
    }
}

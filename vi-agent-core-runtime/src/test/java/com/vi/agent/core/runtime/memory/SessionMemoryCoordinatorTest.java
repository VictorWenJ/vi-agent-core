package com.vi.agent.core.runtime.memory;

import com.vi.agent.core.common.id.ConversationSummaryIdGenerator;
import com.vi.agent.core.common.id.SessionStateSnapshotIdGenerator;
import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.WorkingMode;
import com.vi.agent.core.model.memory.ConfirmedFactRecord;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.InternalTaskStatus;
import com.vi.agent.core.model.memory.InternalTaskType;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.memory.StateDelta;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.MessageStatus;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.port.SessionStateCacheRepository;
import com.vi.agent.core.model.port.SessionStateRepository;
import com.vi.agent.core.model.port.SessionSummaryCacheRepository;
import com.vi.agent.core.model.port.SessionSummaryRepository;
import com.vi.agent.core.model.tool.ToolCallStatus;
import com.vi.agent.core.model.tool.ToolExecution;
import com.vi.agent.core.runtime.memory.extract.StateDeltaExtractionCommand;
import com.vi.agent.core.runtime.memory.extract.StateDeltaExtractionResult;
import com.vi.agent.core.runtime.memory.extract.StateDeltaExtractor;
import com.vi.agent.core.runtime.memory.extract.ConversationSummaryExtractionCommand;
import com.vi.agent.core.runtime.memory.extract.ConversationSummaryExtractionResult;
import com.vi.agent.core.runtime.memory.extract.ConversationSummaryExtractor;
import com.vi.agent.core.runtime.memory.evidence.EvidenceBindingCommand;
import com.vi.agent.core.runtime.memory.evidence.EvidenceBindingResult;
import com.vi.agent.core.runtime.memory.evidence.MemoryEvidenceBinder;
import com.vi.agent.core.runtime.memory.task.InternalMemoryTaskCommand;
import com.vi.agent.core.runtime.memory.task.InternalMemoryTaskExecutor;
import com.vi.agent.core.runtime.memory.task.InternalMemoryTaskResult;
import com.vi.agent.core.runtime.memory.task.InternalMemoryTaskService;
import com.vi.agent.core.runtime.support.TestFieldUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionMemoryCoordinatorTest {

    @Test
    void enabledPostTurnUpdateShouldRunStateAndSummaryTasksWithoutCreatingVersionsForNoop() {
        Fixture fixture = Fixture.create();

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertTrue(result.isSuccess());
        assertFalse(result.isDegraded());
        assertFalse(result.isSkipped());
        assertEquals("task-state", result.getStateTaskId());
        assertEquals("task-summary", result.getSummaryTaskId());
        assertNull(result.getNewStateVersion());
        assertNull(result.getNewSummaryVersion());
        assertEquals(List.of(InternalTaskType.STATE_EXTRACT, InternalTaskType.SUMMARY_EXTRACT, InternalTaskType.EVIDENCE_ENRICH), fixture.taskService.invokedTypes);
        assertNotNull(fixture.taskService.evidenceCommand);
        assertTrue(fixture.taskService.evidenceResult.isSkipped());
        assertEquals(1, fixture.extractor.extractCalls);
        assertEquals(1, fixture.summaryExtractor.extractCalls);
        assertEquals(List.of("msg-user-1", "msg-assistant-1"), fixture.taskService.stateCommand.getMessageIds());
        assertEquals(List.of("msg-user-1", "msg-assistant-1"), fixture.taskService.summaryCommand.getMessageIds());
        assertEquals(List.of("msg-user-1", "msg-assistant-1"), fixture.extractor.lastCommand.getTurnMessages().stream().map(Message::getMessageId).toList());
        assertEquals(List.of("msg-user-1", "msg-assistant-1"), fixture.summaryExtractor.lastCommand.getTurnMessages().stream().map(Message::getMessageId).toList());
        assertTrue(fixture.stateRepository.saved.isEmpty());
        assertTrue(fixture.summaryRepository.saved.isEmpty());
    }

    @Test
    void disabledPostTurnUpdateShouldSkipAllMemoryTasks() {
        Fixture fixture = Fixture.create();
        TestFieldUtils.setField(fixture.coordinator, "properties", new SessionMemoryProperties(false, true, true));

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertTrue(result.isSkipped());
        assertFalse(result.isDegraded());
        assertTrue(fixture.taskService.invokedTypes.isEmpty());
        assertTrue(fixture.stateRepository.saved.isEmpty());
        assertTrue(fixture.summaryRepository.saved.isEmpty());
    }

    @Test
    void nonEmptyStateDeltaShouldMergeSaveNewStateAndRefreshRedisCache() {
        Fixture fixture = Fixture.create();
        fixture.stateRepository.latest = Optional.of(baseState());
        fixture.extractor.result = StateDeltaExtractionResult.builder()
            .success(true)
            .stateDelta(StateDelta.builder()
            .confirmedFactAppend(ConfirmedFactRecord.builder()
                .factId("fact-2")
                .content("new fact")
                .build())
            .sourceCandidateId("msg-user-1")
            .build())
            .sourceCandidateId("msg-user-1")
            .rawOutput("{\"confirmedFactsAppend\":[]}")
            .build();

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertTrue(result.isSuccess());
        assertEquals(2L, result.getNewStateVersion());
        assertEquals(1, fixture.stateRepository.saved.size());
        SessionStateSnapshot saved = fixture.stateRepository.saved.get(0);
        assertEquals("state-fixed-1", saved.getSnapshotId());
        assertEquals("sess-1", saved.getSessionId());
        assertEquals(2L, saved.getStateVersion());
        assertEquals(2, saved.getConfirmedFacts().size());
        assertEquals("new fact", saved.getConfirmedFacts().get(1).getContent());
        assertEquals(saved, fixture.stateCache.saved.get(0));
        assertNotNull(fixture.evidenceBinder.lastCommand);
    }

    @Test
    void nonEmptyStateDeltaShouldCreateVersionOneWhenLatestStateMissing() {
        Fixture fixture = Fixture.create();
        fixture.extractor.result = StateDeltaExtractionResult.builder()
            .success(true)
            .stateDelta(StateDelta.builder()
                .confirmedFactAppend(ConfirmedFactRecord.builder()
                    .factId("fact-1")
                    .content("first fact")
                    .build())
                .build())
            .build();

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertTrue(result.isSuccess());
        assertEquals(1L, result.getNewStateVersion());
        assertEquals(1, fixture.stateRepository.saved.size());
        assertEquals(1L, fixture.stateRepository.saved.get(0).getStateVersion());
    }

    @Test
    void emptyStateDeltaShouldNotSaveNewState() {
        Fixture fixture = Fixture.create();
        fixture.stateRepository.latest = Optional.of(baseState());
        fixture.extractor.result = StateDeltaExtractionResult.builder()
            .success(true)
            .stateDelta(StateDelta.builder().sourceCandidateId("msg-user-1").build())
            .sourceCandidateId("msg-user-1")
            .build();

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertTrue(result.isSuccess());
        assertNull(result.getNewStateVersion());
        assertTrue(fixture.stateRepository.saved.isEmpty());
        assertTrue(fixture.stateCache.saved.isEmpty());
    }

    @Test
    void invalidExtractionShouldDegradeAndNotSaveNewState() {
        Fixture fixture = Fixture.create();
        fixture.stateRepository.latest = Optional.of(baseState());
        fixture.extractor.result = StateDeltaExtractionResult.builder()
            .success(false)
            .degraded(true)
            .failureReason("Invalid StateDelta JSON: unknown field messages")
            .build();

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("Invalid StateDelta JSON"));
        assertTrue(fixture.stateRepository.saved.isEmpty());
        assertTrue(fixture.stateCache.saved.isEmpty());
    }

    @Test
    void mysqlStateSaveFailureShouldDegradeWithoutThrowing() {
        Fixture fixture = Fixture.create();
        fixture.stateRepository.latest = Optional.of(baseState());
        fixture.stateRepository.throwOnSave = true;
        fixture.extractor.result = StateDeltaExtractionResult.builder()
            .success(true)
            .stateDelta(StateDelta.builder()
                .confirmedFactAppend(ConfirmedFactRecord.builder()
                    .factId("fact-2")
                    .content("new fact")
                    .build())
                .build())
            .build();

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("state save failed"));
        assertTrue(fixture.stateCache.saved.isEmpty());
    }

    @Test
    void summaryNoopShouldNotCreateNewSummaryVersion() {
        Fixture fixture = Fixture.create();
        fixture.summaryRepository.latest = Optional.of(baseSummary());

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertTrue(result.isSuccess());
        assertNull(result.getNewSummaryVersion());
        assertTrue(fixture.summaryRepository.saved.isEmpty());
        assertTrue(fixture.summaryCache.saved.isEmpty());
    }

    @Test
    void validSummaryExtractionShouldCreateVersionOneWhenLatestSummaryMissing() {
        Fixture fixture = Fixture.create();
        fixture.summaryExtractor.result = summaryResult("new summary");

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertTrue(result.isSuccess());
        assertFalse(result.isDegraded());
        assertEquals(1L, result.getNewSummaryVersion());
        assertEquals(1, fixture.summaryRepository.saved.size());
        ConversationSummary saved = fixture.summaryRepository.saved.get(0);
        assertEquals("summary-fixed-1", saved.getSummaryId());
        assertEquals("sess-1", saved.getSessionId());
        assertEquals(1L, saved.getSummaryVersion());
        assertEquals(1L, saved.getCoveredFromSequenceNo());
        assertEquals(2L, saved.getCoveredToSequenceNo());
        assertEquals("new summary", saved.getSummaryText());
        assertEquals("summary_extract_inline", saved.getSummaryTemplateKey());
        assertEquals("p2-d-3-v1", saved.getSummaryTemplateVersion());
        assertEquals("fake-provider", saved.getGeneratorProvider());
        assertEquals("fake-model", saved.getGeneratorModel());
        assertEquals(saved, fixture.summaryCache.saved.get(0));
    }

    @Test
    void validSummaryExtractionShouldIncrementLatestSummaryVersionAndExtendCoverage() {
        Fixture fixture = Fixture.create();
        fixture.summaryRepository.latest = Optional.of(baseSummary());
        fixture.summaryExtractor.result = summaryResult("updated summary");

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertTrue(result.isSuccess());
        assertEquals(2L, result.getNewSummaryVersion());
        ConversationSummary saved = fixture.summaryRepository.saved.get(0);
        assertEquals(2L, saved.getSummaryVersion());
        assertEquals(1L, saved.getCoveredFromSequenceNo());
        assertEquals(2L, saved.getCoveredToSequenceNo());
        assertEquals("updated summary", saved.getSummaryText());
    }

    @Test
    void invalidSummaryExtractionShouldDegradeAndNotSaveNewSummary() {
        Fixture fixture = Fixture.create();
        fixture.summaryExtractor.result = ConversationSummaryExtractionResult.builder()
            .success(false)
            .degraded(true)
            .failureReason("invalid summary json")
            .build();

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("invalid summary json"));
        assertTrue(fixture.summaryRepository.saved.isEmpty());
        assertTrue(fixture.summaryCache.saved.isEmpty());
    }

    @Test
    void mysqlSummarySaveFailureShouldDegradeWithoutThrowing() {
        Fixture fixture = Fixture.create();
        fixture.summaryRepository.throwOnSave = true;
        fixture.summaryExtractor.result = summaryResult("new summary");

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("summary save failed"));
        assertTrue(fixture.summaryCache.saved.isEmpty());
    }

    @Test
    void redisSummaryRefreshFailureShouldDegradeWithoutRollingBackMysqlSummarySave() {
        Fixture fixture = Fixture.create();
        fixture.summaryCache.throwOnSave = true;
        fixture.summaryExtractor.result = summaryResult("new summary");

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertEquals(1, fixture.summaryRepository.saved.size());
        assertTrue(result.getFailureReason().contains("Redis summary snapshot refresh failed"));
    }

    @Test
    void stateSuccessAndSummaryFailureShouldNotRollbackStateUpdate() {
        Fixture fixture = Fixture.create();
        fixture.extractor.result = StateDeltaExtractionResult.builder()
            .success(true)
            .stateDelta(StateDelta.builder()
                .confirmedFactAppend(ConfirmedFactRecord.builder()
                    .factId("fact-1")
                    .content("persisted state fact")
                    .build())
                .build())
            .build();
        fixture.summaryExtractor.result = ConversationSummaryExtractionResult.builder()
            .success(false)
            .degraded(true)
            .failureReason("summary failed")
            .build();

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertEquals(1L, result.getNewStateVersion());
        assertEquals(1, fixture.stateRepository.saved.size());
        assertTrue(fixture.summaryRepository.saved.isEmpty());
        assertNotNull(fixture.evidenceBinder.lastCommand);
        assertNotNull(fixture.evidenceBinder.lastCommand.getNewState());
        assertNull(fixture.evidenceBinder.lastCommand.getNewSummary());
    }

    @Test
    void stateAndSummarySuccessShouldBindEvidenceAndExposeSavedCount() {
        Fixture fixture = Fixture.create();
        fixture.extractor.result = StateDeltaExtractionResult.builder()
            .success(true)
            .stateDelta(StateDelta.builder()
                .confirmedFactAppend(ConfirmedFactRecord.builder()
                    .factId("fact-1")
                    .content("new fact")
                    .build())
                .sourceCandidateId("msg-user-1")
                .build())
            .sourceCandidateId("msg-user-1")
            .build();
        fixture.summaryExtractor.result = summaryResult("new summary");
        fixture.evidenceBinder.result = EvidenceBindingResult.builder()
            .success(true)
            .evidenceId("evd-state")
            .evidenceId("evd-summary")
            .stateEvidenceId("evd-state")
            .summaryEvidenceId("evd-summary")
            .savedCount(2)
            .build();

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertTrue(result.isSuccess());
        assertEquals(List.of(InternalTaskType.STATE_EXTRACT, InternalTaskType.SUMMARY_EXTRACT, InternalTaskType.EVIDENCE_ENRICH), fixture.taskService.invokedTypes);
        assertEquals(2, result.getEvidenceSavedCount());
        assertEquals(List.of("evd-state", "evd-summary"), result.getEvidenceIds());
        assertTrue(fixture.taskService.evidenceResult.getOutputJson().contains("\"evidenceIds\":[\"evd-state\",\"evd-summary\"]"));
        assertTrue(fixture.taskService.evidenceResult.getOutputJson().contains("\"stateEvidenceIds\":[\"evd-state\"]"));
        assertTrue(fixture.taskService.evidenceResult.getOutputJson().contains("\"summaryEvidenceIds\":[\"evd-summary\"]"));
        assertTrue(fixture.taskService.evidenceResult.getOutputJson().contains("\"savedCount\":2"));
        assertNotNull(fixture.evidenceBinder.lastCommand);
        assertNotNull(fixture.evidenceBinder.lastCommand.getStateDelta());
        assertNotNull(fixture.evidenceBinder.lastCommand.getNewState());
        assertNotNull(fixture.evidenceBinder.lastCommand.getNewSummary());
        assertEquals(List.of("msg-user-1", "msg-assistant-1"), fixture.evidenceBinder.lastCommand.getTurnMessages().stream().map(Message::getMessageId).toList());
    }

    @Test
    void evidenceFailureShouldDegradeWithoutRollingBackStateOrSummary() {
        Fixture fixture = Fixture.create();
        fixture.extractor.result = StateDeltaExtractionResult.builder()
            .success(true)
            .stateDelta(StateDelta.builder()
                .confirmedFactAppend(ConfirmedFactRecord.builder()
                    .factId("fact-1")
                    .content("new fact")
                    .build())
                .build())
            .build();
        fixture.summaryExtractor.result = summaryResult("new summary");
        fixture.evidenceBinder.result = EvidenceBindingResult.builder()
            .success(false)
            .degraded(true)
            .failureReason("evidence save failed")
            .build();

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertEquals(1, fixture.stateRepository.saved.size());
        assertEquals(1, fixture.summaryRepository.saved.size());
        assertTrue(result.getFailureReason().contains("evidence save failed"));
        assertEquals(InternalTaskStatus.DEGRADED, fixture.taskService.evidenceResult.getStatus());
        assertTrue(fixture.taskService.evidenceResult.getOutputJson().contains("\"failureReason\":\"evidence save failed\""));
    }

    @Test
    void emptyDeltaAndSkippedSummaryShouldRecordEvidenceEnrichSkippedWithoutFakeEvidence() {
        Fixture fixture = Fixture.create();

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertTrue(result.isSuccess());
        assertEquals(InternalTaskType.EVIDENCE_ENRICH, fixture.taskService.evidenceCommand.getTaskType());
        assertTrue(fixture.taskService.evidenceResult.isSkipped());
        assertTrue(fixture.taskService.evidenceResult.getOutputJson().contains("\"skipped\":true"));
        assertTrue(fixture.taskService.evidenceResult.getOutputJson().contains("\"savedCount\":0"));
        assertNull(fixture.evidenceBinder.lastCommand);
        assertTrue(result.getEvidenceIds().isEmpty());
        assertEquals(0, result.getEvidenceSavedCount());
    }

    @Test
    void taskGoalAndWorkingModeUpdateShouldEnterEvidenceBinding() {
        Fixture fixture = Fixture.create();
        fixture.extractor.result = StateDeltaExtractionResult.builder()
            .success(true)
            .stateDelta(StateDelta.builder()
                .taskGoalOverride("new goal")
                .workingModeOverride(WorkingMode.DEBUG_ANALYSIS)
                .sourceCandidateId("msg-user-1")
                .build())
            .sourceCandidateId("msg-user-1")
            .build();
        fixture.evidenceBinder.result = EvidenceBindingResult.builder()
            .success(true)
            .evidenceId("evd-goal")
            .evidenceId("evd-mode")
            .savedCount(2)
            .build();

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertTrue(result.isSuccess());
        assertNotNull(fixture.evidenceBinder.lastCommand);
        assertEquals("new goal", fixture.evidenceBinder.lastCommand.getStateDelta().getTaskGoalOverride());
        assertEquals(WorkingMode.DEBUG_ANALYSIS, fixture.evidenceBinder.lastCommand.getStateDelta().getWorkingModeOverride());
        assertEquals(2, result.getEvidenceSavedCount());
    }

    @Test
    void memoryUpdateExceptionShouldReturnDegradedWithoutThrowing() {
        Fixture fixture = Fixture.create();
        fixture.taskService.throwOnExecute = true;

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertNotNull(result.getFailureReason());
    }

    @Test
    void redisRefreshFailureShouldDegradeWithoutRollingBackMysqlStateSave() {
        Fixture fixture = Fixture.create();
        fixture.stateRepository.latest = Optional.of(baseState());
        fixture.stateCache.throwOnSave = true;
        fixture.extractor.result = StateDeltaExtractionResult.builder()
            .success(true)
            .stateDelta(StateDelta.builder()
                .confirmedFactAppend(ConfirmedFactRecord.builder()
                    .factId("fact-2")
                    .content("new fact")
                    .build())
                .build())
            .build();

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertEquals(1, fixture.stateRepository.saved.size());
        assertTrue(result.getFailureReason().contains("Redis state snapshot refresh failed"));
    }

    private static SessionMemoryUpdateCommand command() {
        return SessionMemoryUpdateCommand.builder()
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .traceId("trace-1")
            .currentUserMessageId("msg-user-1")
            .assistantMessageId("msg-assistant-1")
            .workingContextSnapshotId("wctx-1")
            .agentMode(AgentMode.GENERAL)
            .build();
    }

    private static SessionStateSnapshot baseState() {
        return SessionStateSnapshot.builder()
            .snapshotId("state-1")
            .sessionId("sess-1")
            .stateVersion(1L)
            .taskGoal("old goal")
            .workingMode(WorkingMode.TASK_EXECUTION)
            .confirmedFact(ConfirmedFactRecord.builder()
                .factId("fact-1")
                .content("old fact")
                .build())
            .updatedAt(Instant.now())
            .build();
    }

    private static ConversationSummary baseSummary() {
        return ConversationSummary.builder()
            .summaryId("summary-1")
            .sessionId("sess-1")
            .summaryVersion(1L)
            .coveredFromSequenceNo(1L)
            .coveredToSequenceNo(2L)
            .summaryText("old summary")
            .summaryTemplateKey("summary-v1")
            .summaryTemplateVersion("v1")
            .createdAt(Instant.now())
            .build();
    }

    private static ConversationSummaryExtractionResult summaryResult(String summaryText) {
        return ConversationSummaryExtractionResult.builder()
            .success(true)
            .conversationSummary(ConversationSummary.builder()
                .summaryText(summaryText)
                .build())
            .generatorProvider("fake-provider")
            .generatorModel("fake-model")
            .rawOutput("{\"summaryText\":\"" + summaryText + "\"}")
            .build();
    }

    private static final class Fixture {
        private final SessionMemoryCoordinator coordinator = new SessionMemoryCoordinator();
        private final StubStateRepository stateRepository = new StubStateRepository();
        private final StubSummaryRepository summaryRepository = new StubSummaryRepository();
        private final StubStateCache stateCache = new StubStateCache();
        private final StubSummaryCache summaryCache = new StubSummaryCache();
        private final StubMessageRepository messageRepository = new StubMessageRepository();
        private final StubStateDeltaExtractor extractor = new StubStateDeltaExtractor();
        private final StubConversationSummaryExtractor summaryExtractor = new StubConversationSummaryExtractor();
        private final StubMemoryEvidenceBinder evidenceBinder = new StubMemoryEvidenceBinder();
        private final StubInternalMemoryTaskService taskService = new StubInternalMemoryTaskService();

        private static Fixture create() {
            Fixture fixture = new Fixture();
            TestFieldUtils.setField(fixture.coordinator, "properties", new SessionMemoryProperties(true, true, true));
            TestFieldUtils.setField(fixture.coordinator, "sessionStateRepository", fixture.stateRepository);
            TestFieldUtils.setField(fixture.coordinator, "sessionSummaryRepository", fixture.summaryRepository);
            TestFieldUtils.setField(fixture.coordinator, "sessionStateCacheRepository", fixture.stateCache);
            TestFieldUtils.setField(fixture.coordinator, "sessionSummaryCacheRepository", fixture.summaryCache);
            TestFieldUtils.setField(fixture.coordinator, "messageRepository", fixture.messageRepository);
            TestFieldUtils.setField(fixture.coordinator, "stateDeltaExtractor", fixture.extractor);
            TestFieldUtils.setField(fixture.coordinator, "conversationSummaryExtractor", fixture.summaryExtractor);
            TestFieldUtils.setField(fixture.coordinator, "memoryEvidenceBinder", fixture.evidenceBinder);
            TestFieldUtils.setField(fixture.coordinator, "internalMemoryTaskService", fixture.taskService);
            TestFieldUtils.setField(fixture.coordinator, "stateDeltaMerger", new StateDeltaMerger());
            TestFieldUtils.setField(fixture.coordinator, "sessionStateSnapshotIdGenerator", new FixedSessionStateSnapshotIdGenerator());
            TestFieldUtils.setField(fixture.coordinator, "conversationSummaryIdGenerator", new FixedConversationSummaryIdGenerator());
            return fixture;
        }
    }

    private static final class FixedSessionStateSnapshotIdGenerator extends SessionStateSnapshotIdGenerator {
        private int count;

        @Override
        public String nextId() {
            count++;
            return "state-fixed-" + count;
        }
    }

    private static final class FixedConversationSummaryIdGenerator extends ConversationSummaryIdGenerator {
        private int count;

        @Override
        public String nextId() {
            count++;
            return "summary-fixed-" + count;
        }
    }

    private static final class StubInternalMemoryTaskService extends InternalMemoryTaskService {
        private final List<InternalTaskType> invokedTypes = new ArrayList<>();
        private InternalMemoryTaskCommand stateCommand;
        private InternalMemoryTaskCommand summaryCommand;
        private InternalMemoryTaskCommand evidenceCommand;
        private InternalMemoryTaskResult evidenceResult;
        private boolean throwOnExecute;

        @Override
        public InternalMemoryTaskResult execute(InternalMemoryTaskCommand command) {
            return execute(command, null);
        }

        @Override
        public InternalMemoryTaskResult execute(InternalMemoryTaskCommand command, InternalMemoryTaskExecutor executor) {
            if (throwOnExecute) {
                throw new IllegalStateException("task failed");
            }
            invokedTypes.add(command.getTaskType());
            if (command.getTaskType() == InternalTaskType.STATE_EXTRACT) {
                stateCommand = command;
                return executor.execute("task-state", "{}");
            }
            if (command.getTaskType() == InternalTaskType.EVIDENCE_ENRICH) {
                evidenceCommand = command;
                evidenceResult = executor == null
                    ? InternalMemoryTaskResult.builder()
                        .internalTaskId("task-evidence")
                        .taskType(command.getTaskType())
                        .status(InternalTaskStatus.SKIPPED)
                        .success(true)
                        .skipped(true)
                        .outputJson("""
                            {"success":true,"degraded":false,"skipped":true,"evidenceIds":[],"savedCount":0,"failureReason":null,"stateEvidenceIds":[],"summaryEvidenceIds":[]}
                            """)
                        .build()
                    : executor.execute("task-evidence", "{}");
                return evidenceResult;
            }
            summaryCommand = command;
            return executor.execute("task-summary", "{}");
        }
    }

    private static final class StubStateRepository implements SessionStateRepository {
        private Optional<SessionStateSnapshot> latest = Optional.empty();
        private final List<SessionStateSnapshot> saved = new ArrayList<>();
        private boolean throwOnSave;

        @Override
        public void save(SessionStateSnapshot snapshot) {
            if (throwOnSave) {
                throw new IllegalStateException("state save failed");
            }
            saved.add(snapshot);
            latest = Optional.of(snapshot);
        }

        @Override
        public Optional<SessionStateSnapshot> findBySnapshotId(String snapshotId) {
            return Optional.empty();
        }

        @Override
        public Optional<SessionStateSnapshot> findLatestBySessionId(String sessionId) {
            return latest;
        }

        @Override
        public Optional<SessionStateSnapshot> findBySessionIdAndStateVersion(String sessionId, Long stateVersion) {
            return Optional.empty();
        }
    }

    private static final class StubSummaryRepository implements SessionSummaryRepository {
        private Optional<ConversationSummary> latest = Optional.empty();
        private final List<ConversationSummary> saved = new ArrayList<>();
        private boolean throwOnSave;

        @Override
        public void save(ConversationSummary summary) {
            if (throwOnSave) {
                throw new IllegalStateException("summary save failed");
            }
            saved.add(summary);
            latest = Optional.of(summary);
        }

        @Override
        public Optional<ConversationSummary> findBySummaryId(String summaryId) {
            return Optional.empty();
        }

        @Override
        public Optional<ConversationSummary> findLatestBySessionId(String sessionId) {
            return latest;
        }

        @Override
        public Optional<ConversationSummary> findBySessionIdAndSummaryVersion(String sessionId, Long summaryVersion) {
            return Optional.empty();
        }
    }

    private static final class StubStateCache implements SessionStateCacheRepository {
        private final List<SessionStateSnapshot> saved = new ArrayList<>();
        private boolean throwOnSave;

        @Override
        public Optional<SessionStateSnapshot> findBySessionId(String sessionId) {
            return Optional.empty();
        }

        @Override
        public void save(SessionStateSnapshot snapshot) {
            if (throwOnSave) {
                throw new IllegalStateException("redis down");
            }
            saved.add(snapshot);
        }

        @Override
        public void evict(String sessionId) {
        }
    }

    private static final class StubSummaryCache implements SessionSummaryCacheRepository {
        private final List<ConversationSummary> saved = new ArrayList<>();
        private boolean throwOnSave;

        @Override
        public Optional<ConversationSummary> findBySessionId(String sessionId) {
            return Optional.empty();
        }

        @Override
        public void save(ConversationSummary summary) {
            if (throwOnSave) {
                throw new IllegalStateException("redis summary down");
            }
            saved.add(summary);
        }

        @Override
        public void evict(String sessionId) {
        }
    }

    private static final class StubStateDeltaExtractor implements StateDeltaExtractor {
        private StateDeltaExtractionResult result = StateDeltaExtractionResult.builder()
            .success(true)
            .stateDelta(StateDelta.builder().build())
            .build();
        private int extractCalls;
        private StateDeltaExtractionCommand lastCommand;

        @Override
        public StateDeltaExtractionResult extract(StateDeltaExtractionCommand command) {
            extractCalls++;
            lastCommand = command;
            return result;
        }
    }

    private static final class StubConversationSummaryExtractor implements ConversationSummaryExtractor {
        private ConversationSummaryExtractionResult result = ConversationSummaryExtractionResult.builder()
            .success(true)
            .skipped(true)
            .build();
        private int extractCalls;
        private ConversationSummaryExtractionCommand lastCommand;

        @Override
        public ConversationSummaryExtractionResult extract(ConversationSummaryExtractionCommand command) {
            extractCalls++;
            lastCommand = command;
            return result;
        }
    }

    private static final class StubMemoryEvidenceBinder extends MemoryEvidenceBinder {
        private EvidenceBindingResult result = EvidenceBindingResult.builder()
            .success(true)
            .skipped(true)
            .build();
        private EvidenceBindingCommand lastCommand;

        @Override
        public EvidenceBindingResult bind(EvidenceBindingCommand command) {
            lastCommand = command;
            return result;
        }
    }

    private static final class StubMessageRepository implements MessageRepository {
        private final List<Message> messages = List.of(
            UserMessage.create("msg-user-1", "conv-1", "sess-1", "turn-1", "run-1", 1L, "Please remember this."),
            AssistantMessage.create("msg-assistant-1", "conv-1", "sess-1", "turn-1", "run-1", 2L, "I will remember it.", List.of(), null, null),
            UserMessage.restore("msg-running", "conv-1", "sess-1", "turn-1", "run-1", 3L, MessageStatus.RUNNING, "not completed", Instant.now())
        );

        @Override
        public void saveBatch(List<Message> messages) {
        }

        @Override
        public Optional<Message> findByMessageId(String messageId) {
            return Optional.empty();
        }

        @Override
        public List<Message> findCompletedContextBySessionId(String sessionId, int maxTurns) {
            return List.of();
        }

        @Override
        public List<Message> findByTurnId(String turnId) {
            return messages;
        }

        @Override
        public Optional<Message> findFinalAssistantMessageByTurnId(String turnId) {
            return Optional.empty();
        }

        @Override
        public long nextSequenceNo(String sessionId) {
            return 1L;
        }

        @Override
        public void updateToolCallStatus(String toolCallRecordId, ToolCallStatus status) {
        }

        @Override
        public void upsertToolExecutionRunning(ToolExecution toolExecution) {
        }

        @Override
        public void updateToolExecutionFinal(ToolExecution toolExecution) {
        }
    }
}

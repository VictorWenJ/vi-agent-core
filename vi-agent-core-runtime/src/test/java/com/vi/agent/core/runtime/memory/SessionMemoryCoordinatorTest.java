package com.vi.agent.core.runtime.memory;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.WorkingMode;
import com.vi.agent.core.model.memory.ConfirmedFactRecord;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.EvidenceRef;
import com.vi.agent.core.model.memory.EvidenceTargetType;
import com.vi.agent.core.model.memory.InternalTaskStatus;
import com.vi.agent.core.model.memory.InternalTaskType;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.memory.StateDelta;
import com.vi.agent.core.model.port.MemoryEvidenceRepository;
import com.vi.agent.core.model.port.SessionStateCacheRepository;
import com.vi.agent.core.model.port.SessionStateRepository;
import com.vi.agent.core.model.port.SessionSummaryCacheRepository;
import com.vi.agent.core.model.port.SessionSummaryRepository;
import com.vi.agent.core.runtime.memory.task.InternalMemoryTaskCommand;
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
        assertEquals(List.of(InternalTaskType.STATE_EXTRACT, InternalTaskType.SUMMARY_EXTRACT), fixture.taskService.invokedTypes);
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
        fixture.taskService.stateDelta = StateDelta.builder()
            .confirmedFactAppend(ConfirmedFactRecord.builder()
                .factId("fact-2")
                .content("new fact")
                .build())
            .build();

        SessionMemoryUpdateResult result = fixture.coordinator.updateAfterTurn(command());

        assertTrue(result.isSuccess());
        assertEquals(2L, result.getNewStateVersion());
        assertEquals(1, fixture.stateRepository.saved.size());
        SessionStateSnapshot saved = fixture.stateRepository.saved.get(0);
        assertEquals("sess-1", saved.getSessionId());
        assertEquals(2L, saved.getStateVersion());
        assertEquals(2, saved.getConfirmedFacts().size());
        assertEquals("new fact", saved.getConfirmedFacts().get(1).getContent());
        assertEquals(saved, fixture.stateCache.saved.get(0));
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
        fixture.taskService.stateDelta = StateDelta.builder()
            .confirmedFactAppend(ConfirmedFactRecord.builder()
                .factId("fact-2")
                .content("new fact")
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

    private static final class Fixture {
        private final SessionMemoryCoordinator coordinator = new SessionMemoryCoordinator();
        private final StubStateRepository stateRepository = new StubStateRepository();
        private final StubSummaryRepository summaryRepository = new StubSummaryRepository();
        private final StubStateCache stateCache = new StubStateCache();
        private final StubSummaryCache summaryCache = new StubSummaryCache();
        private final StubInternalMemoryTaskService taskService = new StubInternalMemoryTaskService();

        private static Fixture create() {
            Fixture fixture = new Fixture();
            TestFieldUtils.setField(fixture.coordinator, "properties", new SessionMemoryProperties(true, true, true));
            TestFieldUtils.setField(fixture.coordinator, "sessionStateRepository", fixture.stateRepository);
            TestFieldUtils.setField(fixture.coordinator, "sessionSummaryRepository", fixture.summaryRepository);
            TestFieldUtils.setField(fixture.coordinator, "sessionStateCacheRepository", fixture.stateCache);
            TestFieldUtils.setField(fixture.coordinator, "sessionSummaryCacheRepository", fixture.summaryCache);
            TestFieldUtils.setField(fixture.coordinator, "memoryEvidenceRepository", new StubEvidenceRepository());
            TestFieldUtils.setField(fixture.coordinator, "internalMemoryTaskService", fixture.taskService);
            TestFieldUtils.setField(fixture.coordinator, "stateDeltaMerger", new StateDeltaMerger());
            return fixture;
        }
    }

    private static final class StubInternalMemoryTaskService extends InternalMemoryTaskService {
        private final List<InternalTaskType> invokedTypes = new ArrayList<>();
        private StateDelta stateDelta = StateDelta.builder().build();
        private boolean throwOnExecute;

        @Override
        public InternalMemoryTaskResult execute(InternalMemoryTaskCommand command) {
            if (throwOnExecute) {
                throw new IllegalStateException("task failed");
            }
            invokedTypes.add(command.getTaskType());
            if (command.getTaskType() == InternalTaskType.STATE_EXTRACT) {
                return InternalMemoryTaskResult.builder()
                    .internalTaskId("task-state")
                    .taskType(command.getTaskType())
                    .status(InternalTaskStatus.SUCCEEDED)
                    .success(true)
                    .stateDelta(stateDelta)
                    .build();
            }
            return InternalMemoryTaskResult.builder()
                .internalTaskId("task-summary")
                .taskType(command.getTaskType())
                .status(InternalTaskStatus.SKIPPED)
                .success(true)
                .skipped(true)
                .build();
        }
    }

    private static final class StubStateRepository implements SessionStateRepository {
        private Optional<SessionStateSnapshot> latest = Optional.empty();
        private final List<SessionStateSnapshot> saved = new ArrayList<>();

        @Override
        public void save(SessionStateSnapshot snapshot) {
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

        @Override
        public void save(ConversationSummary summary) {
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

        @Override
        public Optional<ConversationSummary> findBySessionId(String sessionId) {
            return Optional.empty();
        }

        @Override
        public void save(ConversationSummary summary) {
            saved.add(summary);
        }

        @Override
        public void evict(String sessionId) {
        }
    }

    private static final class StubEvidenceRepository implements MemoryEvidenceRepository {
        @Override
        public void save(EvidenceRef evidenceRef) {
        }

        @Override
        public void saveAll(List<EvidenceRef> evidenceRefs) {
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

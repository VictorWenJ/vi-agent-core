package com.vi.agent.core.runtime.context.loader;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.id.WorkingContextSnapshotIdGenerator;
import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.CheckpointTrigger;
import com.vi.agent.core.model.context.ContextViewType;
import com.vi.agent.core.model.context.WorkingContextBuildResult;
import com.vi.agent.core.model.context.WorkingContextSnapshotRecord;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.memory.SessionWorkingSetSnapshot;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.port.SessionStateRepository;
import com.vi.agent.core.model.port.SessionSummaryRepository;
import com.vi.agent.core.model.port.SessionWorkingSetRepository;
import com.vi.agent.core.model.port.WorkingContextSnapshotRepository;
import com.vi.agent.core.runtime.context.ContextTestFixtures;
import com.vi.agent.core.runtime.context.audit.WorkingContextSnapshotService;
import com.vi.agent.core.runtime.context.budget.ContextBudgetCalculator;
import com.vi.agent.core.runtime.context.budget.ContextBudgetProperties;
import com.vi.agent.core.runtime.context.builder.ContextBlockFactory;
import com.vi.agent.core.runtime.context.prompt.ContextBlockPromptVariablesFactory;
import com.vi.agent.core.runtime.context.builder.WorkingContextBuilder;
import com.vi.agent.core.runtime.context.planner.ContextPlanner;
import com.vi.agent.core.runtime.context.policy.ContextPolicyResolver;
import com.vi.agent.core.runtime.context.policy.DefaultContextPolicy;
import com.vi.agent.core.runtime.context.projector.WorkingContextProjector;
import com.vi.agent.core.runtime.context.render.SessionStateBlockRenderer;
import com.vi.agent.core.runtime.context.validation.WorkingContextValidator;
import com.vi.agent.core.runtime.persistence.SessionWorkingSetLoader;
import com.vi.agent.core.runtime.prompt.PromptRuntimeTestSupport;
import com.vi.agent.core.runtime.support.TestFieldUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkingContextLoaderTest {

    @Test
    void loadForMainAgentShouldBuildValidateAndSaveSnapshot() {
        RecordingSnapshotRepository snapshotRepository = new RecordingSnapshotRepository();
        WorkingContextLoader loader = createLoader(new ContextBudgetProperties(1000, 20, 20, 10), snapshotRepository);

        WorkingContextBuildResult result = loader.loadForMainAgent(command());

        assertNotNull(result);
        assertTrue(result.getValidationResult().isValid());
        assertNotNull(snapshotRepository.savedSnapshot);
        assertEquals(ContextTestFixtures.CONVERSATION_ID, snapshotRepository.savedSnapshot.getConversationId());
        assertEquals(AgentMode.GENERAL, snapshotRepository.savedSnapshot.getAgentMode());
        assertEquals(2L, snapshotRepository.savedSnapshot.getWorkingSetVersion());
        assertEquals(3L, snapshotRepository.savedSnapshot.getStateVersion());
        assertEquals(4L, snapshotRepository.savedSnapshot.getSummaryVersion());
        assertNotNull(snapshotRepository.savedSnapshot.getContextJson());
        assertNotNull(snapshotRepository.savedSnapshot.getBlockSetJson());
        assertNotNull(snapshotRepository.savedSnapshot.getProjectionJson());
        assertTrue(snapshotRepository.savedSnapshot.getBlockSetJson().contains("\"sourceType\":\"SESSION_STATE_SNAPSHOT\""));
        assertTrue(snapshotRepository.savedSnapshot.getBlockSetJson().contains("\"sourceVersion\":\"3\""));
    }

    @Test
    void validationFailureShouldSaveSnapshotAndThrow() {
        RecordingSnapshotRepository snapshotRepository = new RecordingSnapshotRepository();
        WorkingContextLoader loader = createLoader(new ContextBudgetProperties(80, 20, 20, 10), snapshotRepository);

        assertThrows(AgentRuntimeException.class, () -> loader.loadForMainAgent(command()));
        assertNotNull(snapshotRepository.savedSnapshot);
    }

    private WorkingContextLoader createLoader(ContextBudgetProperties properties, RecordingSnapshotRepository snapshotRepository) {
        ContextBudgetCalculator budgetCalculator = new ContextBudgetCalculator(properties);
        WorkingContextSnapshotService snapshotService = new WorkingContextSnapshotService();
        WorkingContextBuilder workingContextBuilder = new WorkingContextBuilder();
        TestFieldUtils.setField(snapshotService, "workingContextSnapshotRepository", snapshotRepository);
        TestFieldUtils.setField(workingContextBuilder, "workingContextSnapshotIdGenerator", new WorkingContextSnapshotIdGenerator());

        WorkingContextLoader loader = new WorkingContextLoader();
        TestFieldUtils.setField(loader, "sessionWorkingSetLoader", new StubSessionWorkingSetLoader());
        TestFieldUtils.setField(loader, "sessionWorkingSetRepository", new StubSessionWorkingSetRepository());
        TestFieldUtils.setField(loader, "sessionStateRepository", new StubSessionStateRepository());
        TestFieldUtils.setField(loader, "sessionSummaryRepository", new StubSessionSummaryRepository());
        TestFieldUtils.setField(loader, "contextBudgetCalculator", budgetCalculator);
        TestFieldUtils.setField(loader, "contextBlockFactory", new ContextBlockFactory(
            budgetCalculator,
            new SessionStateBlockRenderer(),
            new com.vi.agent.core.common.id.ContextBlockIdGenerator(),
            PromptRuntimeTestSupport.promptRenderer(),
            new ContextBlockPromptVariablesFactory()
        ));
        TestFieldUtils.setField(loader, "contextPlanner", new ContextPlanner(new ContextPolicyResolver(new DefaultContextPolicy())));
        TestFieldUtils.setField(loader, "workingContextBuilder", workingContextBuilder);
        TestFieldUtils.setField(loader, "workingContextProjector", new WorkingContextProjector());
        TestFieldUtils.setField(loader, "workingContextValidator", new WorkingContextValidator());
        TestFieldUtils.setField(loader, "workingContextSnapshotService", snapshotService);
        return loader;
    }

    private WorkingContextLoadCommand command() {
        return WorkingContextLoadCommand.builder()
            .conversationId(ContextTestFixtures.CONVERSATION_ID)
            .sessionId(ContextTestFixtures.SESSION_ID)
            .turnId(ContextTestFixtures.TURN_ID)
            .runId(ContextTestFixtures.RUN_ID)
            .currentUserMessage(ContextTestFixtures.currentUserMessage())
            .agentMode(AgentMode.GENERAL)
            .contextViewType(ContextViewType.MAIN_AGENT)
            .checkpointTrigger(CheckpointTrigger.BEFORE_FIRST_MODEL_CALL)
            .modelCallSequenceNo(1)
            .build();
    }

    private static final class StubSessionWorkingSetLoader extends SessionWorkingSetLoader {
        @Override
        public List<Message> load(String conversationId, String sessionId) {
            return List.of(ContextTestFixtures.recentUserMessage());
        }
    }

    private static final class StubSessionWorkingSetRepository implements SessionWorkingSetRepository {
        @Override
        public Optional<SessionWorkingSetSnapshot> findBySessionId(String sessionId) {
            return Optional.of(SessionWorkingSetSnapshot.builder()
                .conversationId(ContextTestFixtures.CONVERSATION_ID)
                .sessionId(ContextTestFixtures.SESSION_ID)
                .workingSetVersion(2L)
                .rawMessageIds(List.of("msg-recent"))
                .updatedAt(Instant.now())
                .build());
        }

        @Override
        public void save(SessionWorkingSetSnapshot snapshot) {
        }

        @Override
        public void evict(String sessionId) {
        }
    }

    private static final class StubSessionStateRepository implements SessionStateRepository {
        @Override
        public void save(SessionStateSnapshot snapshot) {
        }

        @Override
        public Optional<SessionStateSnapshot> findBySnapshotId(String snapshotId) {
            return Optional.empty();
        }

        @Override
        public Optional<SessionStateSnapshot> findLatestBySessionId(String sessionId) {
            return Optional.of(ContextTestFixtures.stateSnapshot());
        }

        @Override
        public Optional<SessionStateSnapshot> findBySessionIdAndStateVersion(String sessionId, Long stateVersion) {
            return Optional.empty();
        }
    }

    private static final class StubSessionSummaryRepository implements SessionSummaryRepository {
        @Override
        public void save(ConversationSummary summary) {
        }

        @Override
        public Optional<ConversationSummary> findBySummaryId(String summaryId) {
            return Optional.empty();
        }

        @Override
        public Optional<ConversationSummary> findLatestBySessionId(String sessionId) {
            return Optional.of(ContextTestFixtures.summary());
        }

        @Override
        public Optional<ConversationSummary> findBySessionIdAndSummaryVersion(String sessionId, Long summaryVersion) {
            return Optional.empty();
        }
    }

    private static final class RecordingSnapshotRepository implements WorkingContextSnapshotRepository {
        private WorkingContextSnapshotRecord savedSnapshot;

        @Override
        public void save(WorkingContextSnapshotRecord snapshot) {
            this.savedSnapshot = snapshot;
        }

        @Override
        public Optional<WorkingContextSnapshotRecord> findBySnapshotId(String workingContextSnapshotId) {
            return Optional.ofNullable(savedSnapshot);
        }

        @Override
        public Optional<WorkingContextSnapshotRecord> findLatestBySessionId(String sessionId) {
            return Optional.ofNullable(savedSnapshot);
        }

        @Override
        public List<WorkingContextSnapshotRecord> listByRunId(String runId) {
            return savedSnapshot == null ? List.of() : List.of(savedSnapshot);
        }
    }
}

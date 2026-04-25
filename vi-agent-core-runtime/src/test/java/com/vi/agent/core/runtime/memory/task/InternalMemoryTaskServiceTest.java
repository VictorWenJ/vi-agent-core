package com.vi.agent.core.runtime.memory.task;

import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.memory.InternalLlmTaskRecord;
import com.vi.agent.core.model.memory.InternalTaskStatus;
import com.vi.agent.core.model.memory.InternalTaskType;
import com.vi.agent.core.model.memory.StateDelta;
import com.vi.agent.core.model.port.InternalLlmTaskRepository;
import com.vi.agent.core.runtime.support.TestFieldUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalMemoryTaskServiceTest {

    @Test
    void stateExtractionShouldRecordPendingRunningAndSucceededNoopTask() {
        InternalMemoryTaskService service = new InternalMemoryTaskService();
        RecordingInternalTaskRepository repository = new RecordingInternalTaskRepository();
        TestFieldUtils.setField(service, "internalLlmTaskRepository", repository);

        InternalMemoryTaskResult result = service.execute(command(InternalTaskType.STATE_EXTRACTION));

        assertTrue(result.isSuccess());
        assertFalse(result.isDegraded());
        assertEquals(InternalTaskStatus.SUCCEEDED, result.getStatus());
        assertNotNull(result.getStateDelta());
        assertTrue(result.getStateDelta().isEmpty());
        assertEquals(3, repository.saved.size());
        assertEquals(InternalTaskStatus.PENDING, repository.saved.get(0).getStatus());
        assertEquals(InternalTaskStatus.RUNNING, repository.saved.get(1).getStatus());
        assertEquals(InternalTaskStatus.SUCCEEDED, repository.saved.get(2).getStatus());
        assertTrue(repository.saved.get(0).getRequestJson().contains("\"sessionId\":\"sess-1\""));
        assertTrue(repository.saved.get(2).getResponseJson().contains("\"noop\":true"));
    }

    @Test
    void summaryUpdateShouldRecordSkippedDeterministicTask() {
        InternalMemoryTaskService service = new InternalMemoryTaskService();
        RecordingInternalTaskRepository repository = new RecordingInternalTaskRepository();
        TestFieldUtils.setField(service, "internalLlmTaskRepository", repository);

        InternalMemoryTaskResult result = service.execute(command(InternalTaskType.SUMMARY_UPDATE));

        assertTrue(result.isSuccess());
        assertTrue(result.isSkipped());
        assertEquals(InternalTaskStatus.SKIPPED, result.getStatus());
        assertEquals(3, repository.saved.size());
        assertEquals(InternalTaskStatus.SKIPPED, repository.saved.get(2).getStatus());
        assertTrue(repository.saved.get(2).getResponseJson().contains("\"skipped\":true"));
    }

    @Test
    void taskFailureShouldRecordFailedStatusWithoutThrowing() {
        InternalMemoryTaskService service = new InternalMemoryTaskService() {
            @Override
            protected InternalMemoryTaskResult runDeterministicTask(InternalMemoryTaskCommand command, String internalTaskId) {
                throw new IllegalStateException("boom");
            }
        };
        RecordingInternalTaskRepository repository = new RecordingInternalTaskRepository();
        TestFieldUtils.setField(service, "internalLlmTaskRepository", repository);

        InternalMemoryTaskResult result = service.execute(command(InternalTaskType.STATE_EXTRACTION));

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertEquals(InternalTaskStatus.FAILED, result.getStatus());
        assertTrue(result.getFailureReason().contains("boom"));
        assertEquals(3, repository.saved.size());
        assertEquals(InternalTaskStatus.PENDING, repository.saved.get(0).getStatus());
        assertEquals(InternalTaskStatus.RUNNING, repository.saved.get(1).getStatus());
        assertEquals(InternalTaskStatus.FAILED, repository.saved.get(2).getStatus());
    }

    private InternalMemoryTaskCommand command(InternalTaskType taskType) {
        return InternalMemoryTaskCommand.builder()
            .taskType(taskType)
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

    private static final class RecordingInternalTaskRepository implements InternalLlmTaskRepository {
        private final List<InternalLlmTaskRecord> saved = new ArrayList<>();

        @Override
        public void save(InternalLlmTaskRecord task) {
            saved.add(task);
        }

        @Override
        public Optional<InternalLlmTaskRecord> findByInternalTaskId(String internalTaskId) {
            return saved.stream()
                .filter(record -> record.getInternalTaskId().equals(internalTaskId))
                .reduce((left, right) -> right);
        }

        @Override
        public List<InternalLlmTaskRecord> listBySessionId(String sessionId) {
            return saved;
        }

        @Override
        public List<InternalLlmTaskRecord> listByRunId(String runId) {
            return saved;
        }
    }
}

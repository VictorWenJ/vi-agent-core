package com.vi.agent.core.runtime.memory.task;

import com.vi.agent.core.common.id.InternalTaskIdGenerator;
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
    void stateExtractShouldRecordPendingRunningAndSucceededInlineTask() {
        InternalMemoryTaskService service = new InternalMemoryTaskService();
        RecordingInternalTaskRepository repository = new RecordingInternalTaskRepository();
        TestFieldUtils.setField(service, "internalLlmTaskRepository", repository);
        TestFieldUtils.setField(service, "internalTaskIdGenerator", new FixedInternalTaskIdGenerator());

        InternalMemoryTaskResult result = service.execute(
            stateCommand(),
            (internalTaskId, inputJson) -> InternalMemoryTaskResult.builder()
                .internalTaskId(internalTaskId)
                .taskType(InternalTaskType.STATE_EXTRACT)
                .status(InternalTaskStatus.SUCCEEDED)
                .success(true)
                .stateDelta(StateDelta.builder().sourceCandidateId("msg-user-1").build())
                .newStateVersion(2L)
                .sourceCandidateId("msg-user-1")
                .outputJson("""
                    {"success":true,"degraded":false,"stateDeltaEmpty":true,"newStateVersion":2,"failureReason":null,"sourceCandidateIds":["msg-user-1"]}
                    """)
                .build()
        );

        assertTrue(result.isSuccess());
        assertEquals("itask-fixed", result.getInternalTaskId());
        assertFalse(result.isDegraded());
        assertEquals(InternalTaskStatus.SUCCEEDED, result.getStatus());
        assertNotNull(result.getStateDelta());
        assertTrue(result.getStateDelta().isEmpty());
        assertEquals(2L, result.getNewStateVersion());
        assertEquals(List.of("msg-user-1"), result.getSourceCandidateIds());
        assertEquals(3, repository.saved.size());
        assertEquals(InternalTaskStatus.PENDING, repository.saved.get(0).getStatus());
        assertEquals("itask-fixed", repository.saved.get(0).getInternalTaskId());
        assertEquals(InternalTaskStatus.RUNNING, repository.saved.get(1).getStatus());
        assertEquals(InternalTaskStatus.SUCCEEDED, repository.saved.get(2).getStatus());
        assertTrue(repository.saved.get(0).getRequestJson().contains("\"sessionId\":\"sess-1\""));
        assertTrue(repository.saved.get(0).getRequestJson().contains("\"messageIds\":[\"msg-user-1\",\"msg-assistant-1\"]"));
        assertTrue(repository.saved.get(0).getRequestJson().contains("\"currentStateVersion\":1"));
        assertTrue(repository.saved.get(2).getResponseJson().contains("\"newStateVersion\":2"));
        assertPromptTemplate(repository.saved, "state_extract_inline", "p2-d-2-v1");
    }

    @Test
    void summaryExtractShouldRecordSkippedDeterministicTask() {
        InternalMemoryTaskService service = new InternalMemoryTaskService();
        RecordingInternalTaskRepository repository = new RecordingInternalTaskRepository();
        TestFieldUtils.setField(service, "internalLlmTaskRepository", repository);
        TestFieldUtils.setField(service, "internalTaskIdGenerator", new FixedInternalTaskIdGenerator());

        InternalMemoryTaskResult result = service.execute(summaryCommand());

        assertTrue(result.isSuccess());
        assertEquals("itask-fixed", result.getInternalTaskId());
        assertTrue(result.isSkipped());
        assertEquals(InternalTaskStatus.SKIPPED, result.getStatus());
        assertEquals(3, repository.saved.size());
        assertEquals(InternalTaskStatus.SKIPPED, repository.saved.get(2).getStatus());
        assertTrue(repository.saved.get(2).getResponseJson().contains("\"skipped\":true"));
        assertTrue(repository.saved.get(0).getRequestJson().contains("\"messageIds\":[\"msg-user-1\",\"msg-assistant-1\"]"));
        assertTrue(repository.saved.get(0).getRequestJson().contains("\"latestSummaryVersion\":3"));
        assertTrue(repository.saved.get(0).getRequestJson().contains("\"latestStateVersion\":2"));
        assertPromptTemplate(repository.saved, "summary_extract_inline", "p2-d-3-v1");
    }

    @Test
    void summaryExtractExecutorShouldRecordSucceededOutputWithNewSummaryVersion() {
        InternalMemoryTaskService service = new InternalMemoryTaskService();
        RecordingInternalTaskRepository repository = new RecordingInternalTaskRepository();
        TestFieldUtils.setField(service, "internalLlmTaskRepository", repository);
        TestFieldUtils.setField(service, "internalTaskIdGenerator", new FixedInternalTaskIdGenerator());

        InternalMemoryTaskResult result = service.execute(
            summaryCommand(),
            (internalTaskId, inputJson) -> InternalMemoryTaskResult.builder()
                .internalTaskId(internalTaskId)
                .taskType(InternalTaskType.SUMMARY_EXTRACT)
                .status(InternalTaskStatus.SUCCEEDED)
                .success(true)
                .newSummaryVersion(4L)
                .outputJson("""
                    {"success":true,"degraded":false,"skipped":false,"summaryUpdated":true,"newSummaryVersion":4,"failureReason":null}
                    """)
                .build()
        );

        assertTrue(result.isSuccess());
        assertEquals(4L, result.getNewSummaryVersion());
        assertEquals(InternalTaskStatus.SUCCEEDED, repository.saved.get(2).getStatus());
        assertTrue(repository.saved.get(2).getResponseJson().contains("\"newSummaryVersion\":4"));
        assertPromptTemplate(repository.saved, "summary_extract_inline", "p2-d-3-v1");
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
        TestFieldUtils.setField(service, "internalTaskIdGenerator", new FixedInternalTaskIdGenerator());

        InternalMemoryTaskResult result = service.execute(command(InternalTaskType.STATE_EXTRACT));

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertEquals(InternalTaskStatus.FAILED, result.getStatus());
        assertTrue(result.getFailureReason().contains("boom"));
        assertEquals(3, repository.saved.size());
        assertEquals(InternalTaskStatus.PENDING, repository.saved.get(0).getStatus());
        assertEquals(InternalTaskStatus.RUNNING, repository.saved.get(1).getStatus());
        assertEquals(InternalTaskStatus.FAILED, repository.saved.get(2).getStatus());
        assertPromptTemplate(repository.saved, "state_extract_inline", "p2-d-2-v1");
    }

    private void assertPromptTemplate(List<InternalLlmTaskRecord> records, String expectedKey, String expectedVersion) {
        assertFalse(records.isEmpty());
        for (InternalLlmTaskRecord record : records) {
            assertNotNull(record.getPromptTemplateKey());
            assertFalse(record.getPromptTemplateKey().isBlank());
            assertEquals(expectedKey, record.getPromptTemplateKey());
            assertNotNull(record.getPromptTemplateVersion());
            assertFalse(record.getPromptTemplateVersion().isBlank());
            assertEquals(expectedVersion, record.getPromptTemplateVersion());
        }
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

    private InternalMemoryTaskCommand stateCommand() {
        return InternalMemoryTaskCommand.builder()
            .taskType(InternalTaskType.STATE_EXTRACT)
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .traceId("trace-1")
            .currentUserMessageId("msg-user-1")
            .assistantMessageId("msg-assistant-1")
            .workingContextSnapshotId("wctx-1")
            .agentMode(AgentMode.GENERAL)
            .messageId("msg-user-1")
            .messageId("msg-assistant-1")
            .currentStateVersion(1L)
            .build();
    }

    private InternalMemoryTaskCommand summaryCommand() {
        return InternalMemoryTaskCommand.builder()
            .taskType(InternalTaskType.SUMMARY_EXTRACT)
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .traceId("trace-1")
            .currentUserMessageId("msg-user-1")
            .assistantMessageId("msg-assistant-1")
            .workingContextSnapshotId("wctx-1")
            .agentMode(AgentMode.GENERAL)
            .messageId("msg-user-1")
            .messageId("msg-assistant-1")
            .latestSummaryVersion(3L)
            .latestStateVersion(2L)
            .build();
    }

    private static final class FixedInternalTaskIdGenerator extends InternalTaskIdGenerator {

        @Override
        public String nextId() {
            return "itask-fixed";
        }
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

package com.vi.agent.core.runtime.memory.task;

import com.vi.agent.core.common.id.InternalTaskIdGenerator;
import com.vi.agent.core.model.llm.StructuredOutputChannelResult;
import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.memory.InternalLlmTaskRecord;
import com.vi.agent.core.model.memory.InternalTaskStatus;
import com.vi.agent.core.model.memory.InternalTaskType;
import com.vi.agent.core.model.memory.StateDelta;
import com.vi.agent.core.model.port.InternalLlmTaskRepository;
import com.vi.agent.core.model.prompt.PromptPurpose;
import com.vi.agent.core.model.prompt.PromptRenderMetadata;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import com.vi.agent.core.model.prompt.SystemPromptKey;
import com.vi.agent.core.runtime.prompt.PromptRuntimeTestSupport;
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
        RecordingInternalTaskRepository repository = new RecordingInternalTaskRepository();
        InternalMemoryTaskService service = service(repository);

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
                .promptRenderMetadata(promptMetadata(SystemPromptKey.STATE_DELTA_EXTRACT, StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT))
                .structuredOutputChannelResult(StructuredOutputChannelResult.builder()
                    .success(true)
                    .actualStructuredOutputMode(StructuredLlmOutputMode.JSON_OBJECT)
                    .retryCount(0)
                    .build())
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
        assertPromptTemplate(repository.saved, "state_delta_extract", PromptRuntimeTestSupport.CATALOG_REVISION);
        assertPromptAudit(repository.saved.get(2), "state_delta_extract", "state_delta_output");
        assertTrue(repository.saved.get(2).getRequestJson().contains("\"actualStructuredOutputMode\":\"json_object\""));
    }

    @Test
    void summaryExtractShouldRecordSkippedDeterministicTask() {
        RecordingInternalTaskRepository repository = new RecordingInternalTaskRepository();
        InternalMemoryTaskService service = service(repository);

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
        assertPromptTemplate(repository.saved, "conversation_summary_extract", PromptRuntimeTestSupport.CATALOG_REVISION);
        assertPromptAudit(repository.saved.get(2), "conversation_summary_extract", "conversation_summary_output");
    }

    @Test
    void summaryExtractExecutorShouldRecordSucceededOutputWithNewSummaryVersion() {
        RecordingInternalTaskRepository repository = new RecordingInternalTaskRepository();
        InternalMemoryTaskService service = service(repository);

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
        assertPromptTemplate(repository.saved, "conversation_summary_extract", PromptRuntimeTestSupport.CATALOG_REVISION);
        assertPromptAudit(repository.saved.get(2), "conversation_summary_extract", "conversation_summary_output");
    }

    @Test
    void evidenceEnrichShouldRecordDeterministicAuditOutput() {
        RecordingInternalTaskRepository repository = new RecordingInternalTaskRepository();
        InternalMemoryTaskService service = service(repository);

        InternalMemoryTaskResult result = service.execute(
            evidenceCommand(),
            (internalTaskId, inputJson) -> InternalMemoryTaskResult.builder()
                .internalTaskId(internalTaskId)
                .taskType(InternalTaskType.EVIDENCE_ENRICH)
                .status(InternalTaskStatus.SUCCEEDED)
                .success(true)
                .outputJson("""
                    {"success":true,"degraded":false,"skipped":false,"evidenceIds":["evd-1"],"savedCount":1,"failureReason":null,"stateEvidenceIds":["evd-1"],"summaryEvidenceIds":[]}
                    """)
                .build()
        );

        assertTrue(result.isSuccess());
        assertEquals(InternalTaskStatus.SUCCEEDED, result.getStatus());
        assertEquals(3, repository.saved.size());
        assertEquals(InternalTaskStatus.PENDING, repository.saved.get(0).getStatus());
        assertEquals(InternalTaskStatus.RUNNING, repository.saved.get(1).getStatus());
        assertEquals(InternalTaskStatus.SUCCEEDED, repository.saved.get(2).getStatus());
        assertTrue(repository.saved.get(0).getRequestJson().contains("\"stateTaskId\":\"task-state\""));
        assertTrue(repository.saved.get(0).getRequestJson().contains("\"summaryTaskId\":\"task-summary\""));
        assertTrue(repository.saved.get(0).getRequestJson().contains("\"stateUpdated\":true"));
        assertTrue(repository.saved.get(0).getRequestJson().contains("\"summaryUpdated\":false"));
        assertTrue(repository.saved.get(0).getRequestJson().contains("\"sourceCandidateIds\":[\"msg-user-1\"]"));
        assertTrue(repository.saved.get(2).getResponseJson().contains("\"evidenceIds\":[\"evd-1\"]"));
        assertTrue(repository.saved.get(2).getResponseJson().contains("\"savedCount\":1"));
        assertPromptTemplate(repository.saved, "evidence_bind_deterministic", PromptRuntimeTestSupport.CATALOG_REVISION);
    }

    @Test
    void evidenceEnrichDefaultTaskShouldRecordSkippedStatus() {
        RecordingInternalTaskRepository repository = new RecordingInternalTaskRepository();
        InternalMemoryTaskService service = service(repository);

        InternalMemoryTaskResult result = service.execute(evidenceCommand());

        assertTrue(result.isSuccess());
        assertTrue(result.isSkipped());
        assertEquals(InternalTaskStatus.SKIPPED, result.getStatus());
        assertTrue(repository.saved.get(2).getResponseJson().contains("\"skipped\":true"));
        assertTrue(repository.saved.get(2).getResponseJson().contains("\"savedCount\":0"));
        assertPromptTemplate(repository.saved, "evidence_bind_deterministic", PromptRuntimeTestSupport.CATALOG_REVISION);
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
        service = service(repository, service);

        InternalMemoryTaskResult result = service.execute(command(InternalTaskType.STATE_EXTRACT));

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertEquals(InternalTaskStatus.FAILED, result.getStatus());
        assertTrue(result.getFailureReason().contains("boom"));
        assertEquals(3, repository.saved.size());
        assertEquals(InternalTaskStatus.PENDING, repository.saved.get(0).getStatus());
        assertEquals(InternalTaskStatus.RUNNING, repository.saved.get(1).getStatus());
        assertEquals(InternalTaskStatus.FAILED, repository.saved.get(2).getStatus());
        assertPromptTemplate(repository.saved, "state_delta_extract", PromptRuntimeTestSupport.CATALOG_REVISION);
        assertPromptAudit(repository.saved.get(2), "state_delta_extract", "state_delta_output");
    }

    private InternalMemoryTaskService service(RecordingInternalTaskRepository repository) {
        return new InternalMemoryTaskService(
            repository,
            new FixedInternalTaskIdGenerator(),
            new InternalTaskPromptResolver(PromptRuntimeTestSupport.systemPromptRegistry())
        );
    }

    private InternalMemoryTaskService service(
        RecordingInternalTaskRepository repository,
        InternalMemoryTaskService template
    ) {
        return new InternalMemoryTaskService(
            repository,
            new FixedInternalTaskIdGenerator(),
            new InternalTaskPromptResolver(PromptRuntimeTestSupport.systemPromptRegistry())
        ) {
            @Override
            protected InternalMemoryTaskResult runDeterministicTask(InternalMemoryTaskCommand command, String internalTaskId) {
                return template.runDeterministicTask(command, internalTaskId);
            }
        };
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

    private void assertPromptAudit(InternalLlmTaskRecord record, String expectedPromptKey, String expectedContractKey) {
        assertTrue(record.getRequestJson().contains("\"promptAudit\""));
        assertTrue(record.getRequestJson().contains("\"promptKey\":\"" + expectedPromptKey + "\""));
        assertTrue(record.getRequestJson().contains("\"structuredOutputContractKey\":\"" + expectedContractKey + "\""));
        assertTrue(record.getRequestJson().contains("\"templateContentHash\""));
        assertTrue(record.getRequestJson().contains("\"manifestContentHash\""));
        assertTrue(record.getRequestJson().contains("\"contractContentHash\""));
        assertTrue(record.getRequestJson().contains("\"catalogRevision\":\"" + PromptRuntimeTestSupport.CATALOG_REVISION + "\""));
        assertTrue(record.getRequestJson().contains("\"retryCount\":0"));
        assertTrue(record.getRequestJson().contains("\"failureReason\""));
    }

    private PromptRenderMetadata promptMetadata(
        SystemPromptKey promptKey,
        StructuredLlmOutputContractKey contractKey
    ) {
        return PromptRenderMetadata.builder()
            .promptKey(promptKey)
            .purpose(promptKey == SystemPromptKey.STATE_DELTA_EXTRACT
                ? PromptPurpose.STATE_DELTA_EXTRACTION
                : PromptPurpose.CONVERSATION_SUMMARY_EXTRACTION)
            .structuredOutputContractKey(contractKey)
            .templateContentHash("template-hash")
            .manifestContentHash("manifest-hash")
            .contractContentHash("contract-hash")
            .catalogRevision(PromptRuntimeTestSupport.CATALOG_REVISION)
            .renderedVariableName("turnMessagesText")
            .build();
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

    private InternalMemoryTaskCommand evidenceCommand() {
        return InternalMemoryTaskCommand.builder()
            .taskType(InternalTaskType.EVIDENCE_ENRICH)
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
            .stateTaskId("task-state")
            .summaryTaskId("task-summary")
            .stateUpdated(true)
            .summaryUpdated(false)
            .sourceCandidateId("msg-user-1")
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

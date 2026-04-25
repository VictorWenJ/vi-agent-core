package com.vi.agent.core.infra.persistence.mysql.repository;

import com.vi.agent.core.infra.persistence.mysql.entity.AgentInternalLlmTaskEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentInternalLlmTaskMapper;
import com.vi.agent.core.model.context.CheckpointTrigger;
import com.vi.agent.core.model.memory.InternalLlmTaskRecord;
import com.vi.agent.core.model.memory.InternalTaskStatus;
import com.vi.agent.core.model.memory.InternalTaskType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysqlInternalLlmTaskRepositoryTest {

    @BeforeAll
    static void initTableMetadata() {
        MysqlRepositoryTestSupport.initTableInfoIfAbsent(AgentInternalLlmTaskEntity.class);
    }

    @Test
    void saveShouldInsertInternalLlmTaskEntity() {
        MysqlInternalLlmTaskRepository repository = new MysqlInternalLlmTaskRepository();
        AgentInternalLlmTaskMapper mapper = Mockito.mock(AgentInternalLlmTaskMapper.class);
        MysqlRepositoryTestSupport.setField(repository, "internalLlmTaskMapper", mapper);

        repository.save(task());

        ArgumentCaptor<AgentInternalLlmTaskEntity> captor = ArgumentCaptor.forClass(AgentInternalLlmTaskEntity.class);
        verify(mapper).insert(captor.capture());
        AgentInternalLlmTaskEntity entity = captor.getValue();
        assertEquals("task-1", entity.getInternalTaskId());
        assertEquals("STATE_EXTRACT", entity.getTaskType());
        assertEquals("POST_TURN", entity.getCheckpointTrigger());
        assertEquals("state_extract_noop", entity.getPromptTemplateKey());
        assertEquals("p2-d-1-v1", entity.getPromptTemplateVersion());
        assertEquals("SUCCEEDED", entity.getStatus());
        assertEquals("{\"request\":true}", entity.getRequestJson());
        assertEquals("{\"response\":true}", entity.getResponseJson());
    }

    @Test
    void findByInternalTaskIdShouldReturnTaskRecord() {
        MysqlInternalLlmTaskRepository repository = new MysqlInternalLlmTaskRepository();
        AgentInternalLlmTaskMapper mapper = Mockito.mock(AgentInternalLlmTaskMapper.class);
        MysqlRepositoryTestSupport.setField(repository, "internalLlmTaskMapper", mapper);

        AgentInternalLlmTaskEntity entity = new AgentInternalLlmTaskEntity();
        entity.setInternalTaskId("task-1");
        entity.setTaskType("STATE_EXTRACT");
        entity.setSessionId("sess-1");
        entity.setTurnId("turn-1");
        entity.setRunId("run-1");
        entity.setCheckpointTrigger("POST_TURN");
        entity.setPromptTemplateKey("state_extract_noop");
        entity.setPromptTemplateVersion("p2-d-1-v1");
        entity.setRequestJson("{\"request\":true}");
        entity.setResponseJson("{\"response\":true}");
        entity.setStatus("SUCCEEDED");
        entity.setDurationMs(33L);
        entity.setCreatedAt(LocalDateTime.of(2026, 4, 25, 0, 0));
        entity.setCompletedAt(LocalDateTime.of(2026, 4, 25, 0, 0, 1));
        when(mapper.selectOne(any())).thenReturn(entity);

        var result = repository.findByInternalTaskId("task-1");

        assertTrue(result.isPresent());
        assertEquals(CheckpointTrigger.POST_TURN, result.get().getCheckpointTrigger());
        assertEquals(InternalTaskStatus.SUCCEEDED, result.get().getStatus());
        assertEquals("state_extract_noop", result.get().getPromptTemplateKey());
        assertEquals("p2-d-1-v1", result.get().getPromptTemplateVersion());
        assertEquals(33L, result.get().getDurationMs());
    }

    private InternalLlmTaskRecord task() {
        return InternalLlmTaskRecord.builder()
            .internalTaskId("task-1")
            .taskType(InternalTaskType.STATE_EXTRACT)
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .checkpointTrigger(CheckpointTrigger.POST_TURN)
            .promptTemplateKey("state_extract_noop")
            .promptTemplateVersion("p2-d-1-v1")
            .requestJson("{\"request\":true}")
            .responseJson("{\"response\":true}")
            .status(InternalTaskStatus.SUCCEEDED)
            .durationMs(33L)
            .createdAt(Instant.parse("2026-04-25T00:00:00Z"))
            .completedAt(Instant.parse("2026-04-25T00:00:01Z"))
            .build();
    }
}

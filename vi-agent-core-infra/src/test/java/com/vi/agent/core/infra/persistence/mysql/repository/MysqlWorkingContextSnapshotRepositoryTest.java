package com.vi.agent.core.infra.persistence.mysql.repository;

import com.vi.agent.core.infra.persistence.mysql.entity.AgentWorkingContextSnapshotEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentWorkingContextSnapshotMapper;
import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.CheckpointTrigger;
import com.vi.agent.core.model.context.ContextViewType;
import com.vi.agent.core.model.context.WorkingContextSnapshotRecord;
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

class MysqlWorkingContextSnapshotRepositoryTest {

    @BeforeAll
    static void initTableMetadata() {
        MysqlRepositoryTestSupport.initTableInfoIfAbsent(AgentWorkingContextSnapshotEntity.class);
    }

    @Test
    void saveShouldInsertWorkingContextSnapshotEntity() {
        MysqlWorkingContextSnapshotRepository repository = new MysqlWorkingContextSnapshotRepository();
        AgentWorkingContextSnapshotMapper mapper = Mockito.mock(AgentWorkingContextSnapshotMapper.class);
        MysqlRepositoryTestSupport.setField(repository, "workingContextSnapshotMapper", mapper);

        repository.save(snapshot());

        ArgumentCaptor<AgentWorkingContextSnapshotEntity> captor = ArgumentCaptor.forClass(AgentWorkingContextSnapshotEntity.class);
        verify(mapper).insert(captor.capture());
        AgentWorkingContextSnapshotEntity entity = captor.getValue();
        assertEquals("wcs-1", entity.getWorkingContextSnapshotId());
        assertEquals("conv-1", entity.getConversationId());
        assertEquals("POST_TURN", entity.getCheckpointTrigger());
        assertEquals("MAIN_AGENT", entity.getContextViewType());
        assertEquals("GENERAL", entity.getAgentMode());
        assertEquals("{\"budget\":true}", entity.getBudgetJson());
        assertEquals("{\"blocks\":[]}", entity.getBlockSetJson());
        assertEquals("{\"context\":true}", entity.getContextJson());
        assertEquals("{\"projection\":true}", entity.getProjectionJson());
    }

    @Test
    void findBySnapshotIdShouldReturnWorkingContextSnapshotRecord() {
        MysqlWorkingContextSnapshotRepository repository = new MysqlWorkingContextSnapshotRepository();
        AgentWorkingContextSnapshotMapper mapper = Mockito.mock(AgentWorkingContextSnapshotMapper.class);
        MysqlRepositoryTestSupport.setField(repository, "workingContextSnapshotMapper", mapper);

        AgentWorkingContextSnapshotEntity entity = new AgentWorkingContextSnapshotEntity();
        entity.setWorkingContextSnapshotId("wcs-1");
        entity.setConversationId("conv-1");
        entity.setSessionId("sess-1");
        entity.setTurnId("turn-1");
        entity.setRunId("run-1");
        entity.setContextBuildSeq(1);
        entity.setModelCallSequenceNo(2);
        entity.setCheckpointTrigger("POST_TURN");
        entity.setContextViewType("MAIN_AGENT");
        entity.setAgentMode("GENERAL");
        entity.setTranscriptSnapshotVersion(11L);
        entity.setWorkingSetVersion(12L);
        entity.setStateVersion(13L);
        entity.setSummaryVersion(14L);
        entity.setBudgetJson("{\"budget\":true}");
        entity.setBlockSetJson("{\"blocks\":[]}");
        entity.setContextJson("{\"context\":true}");
        entity.setProjectionJson("{\"projection\":true}");
        entity.setCreatedAt(LocalDateTime.of(2026, 4, 25, 0, 0));
        when(mapper.selectOne(any())).thenReturn(entity);

        var result = repository.findBySnapshotId("wcs-1");

        assertTrue(result.isPresent());
        assertEquals(CheckpointTrigger.POST_TURN, result.get().getCheckpointTrigger());
        assertEquals(ContextViewType.MAIN_AGENT, result.get().getContextViewType());
        assertEquals(AgentMode.GENERAL, result.get().getAgentMode());
        assertEquals("{\"projection\":true}", result.get().getProjectionJson());
    }

    private WorkingContextSnapshotRecord snapshot() {
        return WorkingContextSnapshotRecord.builder()
            .workingContextSnapshotId("wcs-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .contextBuildSeq(1)
            .modelCallSequenceNo(2)
            .checkpointTrigger(CheckpointTrigger.POST_TURN)
            .contextViewType(ContextViewType.MAIN_AGENT)
            .agentMode(AgentMode.GENERAL)
            .transcriptSnapshotVersion(11L)
            .workingSetVersion(12L)
            .stateVersion(13L)
            .summaryVersion(14L)
            .budgetJson("{\"budget\":true}")
            .blockSetJson("{\"blocks\":[]}")
            .contextJson("{\"context\":true}")
            .projectionJson("{\"projection\":true}")
            .createdAt(Instant.parse("2026-04-25T00:00:00Z"))
            .build();
    }
}

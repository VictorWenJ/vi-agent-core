package com.vi.agent.core.infra.persistence.mysql.repository;

import com.vi.agent.core.infra.persistence.mysql.entity.AgentSessionStateEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentSessionStateMapper;
import com.vi.agent.core.model.context.WorkingMode;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysqlSessionStateRepositoryTest {

    @BeforeAll
    static void initTableMetadata() {
        MysqlRepositoryTestSupport.initTableInfoIfAbsent(AgentSessionStateEntity.class);
    }

    @Test
    void saveShouldInsertSessionStateEntity() {
        MysqlSessionStateRepository repository = new MysqlSessionStateRepository();
        AgentSessionStateMapper mapper = Mockito.mock(AgentSessionStateMapper.class);
        MysqlRepositoryTestSupport.setField(repository, "sessionStateMapper", mapper);

        SessionStateSnapshot snapshot = stateSnapshot();

        repository.save(snapshot);

        ArgumentCaptor<AgentSessionStateEntity> captor = ArgumentCaptor.forClass(AgentSessionStateEntity.class);
        verify(mapper).insert(captor.capture());
        AgentSessionStateEntity entity = captor.getValue();
        assertEquals("state-1", entity.getSnapshotId());
        assertEquals("sess-1", entity.getSessionId());
        assertEquals(7L, entity.getStateVersion());
        assertEquals("finish P2-B", entity.getTaskGoal());
        assertTrue(entity.getStateJson().contains("\"snapshotId\":\"state-1\""));
        assertNull(entity.getSourceRunId());
    }

    @Test
    void findLatestBySessionIdShouldReturnStateFromStateJson() {
        MysqlSessionStateRepository repository = new MysqlSessionStateRepository();
        AgentSessionStateMapper mapper = Mockito.mock(AgentSessionStateMapper.class);
        MysqlRepositoryTestSupport.setField(repository, "sessionStateMapper", mapper);

        AgentSessionStateEntity entity = new AgentSessionStateEntity();
        entity.setStateJson(com.vi.agent.core.common.util.JsonUtils.toJson(stateSnapshot()));
        when(mapper.selectOne(any())).thenReturn(entity);

        var result = repository.findLatestBySessionId("sess-1");

        assertTrue(result.isPresent());
        assertEquals("state-1", result.get().getSnapshotId());
        assertEquals(WorkingMode.TASK_EXECUTION, result.get().getWorkingMode());
    }

    private SessionStateSnapshot stateSnapshot() {
        return SessionStateSnapshot.builder()
            .snapshotId("state-1")
            .sessionId("sess-1")
            .stateVersion(7L)
            .taskGoal("finish P2-B")
            .workingMode(WorkingMode.TASK_EXECUTION)
            .updatedAt(Instant.parse("2026-04-25T00:00:00Z"))
            .build();
    }
}

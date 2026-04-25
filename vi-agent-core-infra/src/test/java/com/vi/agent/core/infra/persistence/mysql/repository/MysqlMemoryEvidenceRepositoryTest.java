package com.vi.agent.core.infra.persistence.mysql.repository;

import com.vi.agent.core.infra.persistence.mysql.entity.AgentMemoryEvidenceEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentMemoryEvidenceMapper;
import com.vi.agent.core.model.memory.EvidenceRef;
import com.vi.agent.core.model.memory.EvidenceSource;
import com.vi.agent.core.model.memory.EvidenceSourceType;
import com.vi.agent.core.model.memory.EvidenceTarget;
import com.vi.agent.core.model.memory.EvidenceTargetType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysqlMemoryEvidenceRepositoryTest {

    @BeforeAll
    static void initTableMetadata() {
        MysqlRepositoryTestSupport.initTableInfoIfAbsent(AgentMemoryEvidenceEntity.class);
    }

    @Test
    void saveShouldInsertMemoryEvidenceEntity() {
        MysqlMemoryEvidenceRepository repository = new MysqlMemoryEvidenceRepository();
        AgentMemoryEvidenceMapper mapper = Mockito.mock(AgentMemoryEvidenceMapper.class);
        MysqlRepositoryTestSupport.setField(repository, "memoryEvidenceMapper", mapper);

        repository.save(evidenceRef());

        ArgumentCaptor<AgentMemoryEvidenceEntity> captor = ArgumentCaptor.forClass(AgentMemoryEvidenceEntity.class);
        verify(mapper).insert(captor.capture());
        AgentMemoryEvidenceEntity entity = captor.getValue();
        assertEquals("ev-1", entity.getEvidenceId());
        assertEquals("sess-1", entity.getSessionId());
        assertEquals("SESSION_STATE_FIELD", entity.getTargetType());
        assertEquals("state-1", entity.getTargetRef());
        assertEquals("constraints", entity.getTargetField());
        assertEquals("constraint-1", entity.getTargetItemId());
        assertEquals("constraints[0]", entity.getDisplayPath());
        assertEquals("USER_MESSAGE", entity.getSourceType());
        assertEquals(new BigDecimal("0.99"), entity.getConfidence());
    }

    @Test
    void findByEvidenceIdShouldReturnEvidenceRef() {
        MysqlMemoryEvidenceRepository repository = new MysqlMemoryEvidenceRepository();
        AgentMemoryEvidenceMapper mapper = Mockito.mock(AgentMemoryEvidenceMapper.class);
        MysqlRepositoryTestSupport.setField(repository, "memoryEvidenceMapper", mapper);

        AgentMemoryEvidenceEntity entity = new AgentMemoryEvidenceEntity();
        entity.setEvidenceId("ev-1");
        entity.setSessionId("sess-1");
        entity.setTurnId("turn-1");
        entity.setRunId("run-1");
        entity.setTargetType("SESSION_STATE_FIELD");
        entity.setTargetRef("state-1");
        entity.setTargetField("constraints");
        entity.setTargetItemId("constraint-1");
        entity.setDisplayPath("constraints[0]");
        entity.setSourceType("USER_MESSAGE");
        entity.setMessageId("msg-1");
        entity.setExcerptText("用户明确要求");
        entity.setConfidence(new BigDecimal("0.99"));
        entity.setCreatedAt(LocalDateTime.of(2026, 4, 25, 0, 0));
        when(mapper.selectOne(any())).thenReturn(entity);

        var result = repository.findByEvidenceId("ev-1");

        assertTrue(result.isPresent());
        assertEquals(EvidenceTargetType.SESSION_STATE_FIELD, result.get().getTarget().getTargetType());
        assertEquals("state-1", result.get().getTarget().getTargetRef());
        assertEquals("constraints", result.get().getTarget().getTargetField());
        assertEquals("constraint-1", result.get().getTarget().getTargetItemId());
        assertEquals("constraints[0]", result.get().getTarget().getDisplayPath());
        assertEquals(EvidenceSourceType.USER_MESSAGE, result.get().getSource().getSourceType());
        assertEquals(0.99D, result.get().getConfidence());
    }

    private EvidenceRef evidenceRef() {
        return EvidenceRef.builder()
            .evidenceId("ev-1")
            .target(EvidenceTarget.builder()
                .targetType(EvidenceTargetType.SESSION_STATE_FIELD)
                .targetRef("state-1")
                .targetField("constraints")
                .targetItemId("constraint-1")
                .displayPath("constraints[0]")
                .build())
            .source(EvidenceSource.builder()
                .sourceType(EvidenceSourceType.USER_MESSAGE)
                .sessionId("sess-1")
                .turnId("turn-1")
                .runId("run-1")
                .messageId("msg-1")
                .excerptText("用户明确要求")
                .build())
            .confidence(0.99D)
            .createdAt(Instant.parse("2026-04-25T00:00:00Z"))
            .build();
    }
}

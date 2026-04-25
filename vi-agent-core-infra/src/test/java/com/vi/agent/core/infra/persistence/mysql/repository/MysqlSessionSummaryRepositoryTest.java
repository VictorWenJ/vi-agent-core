package com.vi.agent.core.infra.persistence.mysql.repository;

import com.vi.agent.core.infra.persistence.mysql.entity.AgentSessionSummaryEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentSessionSummaryMapper;
import com.vi.agent.core.model.memory.ConversationSummary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysqlSessionSummaryRepositoryTest {

    @BeforeAll
    static void initTableMetadata() {
        MysqlRepositoryTestSupport.initTableInfoIfAbsent(AgentSessionSummaryEntity.class);
    }

    @Test
    void saveShouldInsertSessionSummaryEntity() {
        MysqlSessionSummaryRepository repository = new MysqlSessionSummaryRepository();
        AgentSessionSummaryMapper mapper = Mockito.mock(AgentSessionSummaryMapper.class);
        MysqlRepositoryTestSupport.setField(repository, "sessionSummaryMapper", mapper);

        repository.save(summary());

        ArgumentCaptor<AgentSessionSummaryEntity> captor = ArgumentCaptor.forClass(AgentSessionSummaryEntity.class);
        verify(mapper).insert(captor.capture());
        AgentSessionSummaryEntity entity = captor.getValue();
        assertEquals("sum-1", entity.getSummaryId());
        assertEquals("sess-1", entity.getSessionId());
        assertEquals(2L, entity.getSummaryVersion());
        assertEquals(10L, entity.getCoveredToSequenceNo());
        assertEquals("deepseek-chat", entity.getGeneratorModel());
        assertNull(entity.getSourceRunId());
    }

    @Test
    void findBySummaryIdShouldReturnConversationSummary() {
        MysqlSessionSummaryRepository repository = new MysqlSessionSummaryRepository();
        AgentSessionSummaryMapper mapper = Mockito.mock(AgentSessionSummaryMapper.class);
        MysqlRepositoryTestSupport.setField(repository, "sessionSummaryMapper", mapper);

        AgentSessionSummaryEntity entity = new AgentSessionSummaryEntity();
        entity.setSummaryId("sum-1");
        entity.setSessionId("sess-1");
        entity.setSummaryVersion(2L);
        entity.setCoveredFromSequenceNo(1L);
        entity.setCoveredToSequenceNo(10L);
        entity.setSummaryText("历史摘要");
        entity.setSummaryTemplateKey("summary_extract");
        entity.setSummaryTemplateVersion("v1");
        entity.setGeneratorProvider("deepseek");
        entity.setGeneratorModel("deepseek-chat");
        entity.setCreatedAt(LocalDateTime.of(2026, 4, 25, 0, 0));
        when(mapper.selectOne(any())).thenReturn(entity);

        var result = repository.findBySummaryId("sum-1");

        assertTrue(result.isPresent());
        assertEquals("历史摘要", result.get().getSummaryText());
        assertEquals(10L, result.get().getCoveredToSequenceNo());
    }

    private ConversationSummary summary() {
        return ConversationSummary.builder()
            .summaryId("sum-1")
            .sessionId("sess-1")
            .summaryVersion(2L)
            .coveredFromSequenceNo(1L)
            .coveredToSequenceNo(10L)
            .summaryText("历史摘要")
            .summaryTemplateKey("summary_extract")
            .summaryTemplateVersion("v1")
            .generatorProvider("deepseek")
            .generatorModel("deepseek-chat")
            .createdAt(Instant.parse("2026-04-25T00:00:00Z"))
            .build();
    }
}

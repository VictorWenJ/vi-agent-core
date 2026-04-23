package com.vi.agent.core.infra.persistence.mysql.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.vi.agent.core.infra.persistence.message.handler.MessageTypeHandlerRegistry;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageToolCallEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentToolExecutionEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentMessageMapper;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentMessageToolCallMapper;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentToolExecutionMapper;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentTurnMapper;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.tool.ToolCallStatus;
import com.vi.agent.core.model.tool.ToolExecution;
import com.vi.agent.core.model.tool.ToolExecutionStatus;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class MysqlMessageRepositoryToolStatusTest {

    @BeforeAll
    static void initTableMetadata() {
        initTableInfoIfAbsent(AgentMessageEntity.class);
        initTableInfoIfAbsent(AgentMessageToolCallEntity.class);
        initTableInfoIfAbsent(AgentToolExecutionEntity.class);
    }

    @Test
    void toolCallStatusShouldProgressByStage() {
        MysqlMessageRepository repository = new MysqlMessageRepository();

        AgentMessageToolCallMapper toolCallMapper = Mockito.mock(AgentMessageToolCallMapper.class);
        setCoreFields(repository, toolCallMapper, Mockito.mock(AgentToolExecutionMapper.class));

        when(toolCallMapper.selectOne(any())).thenReturn(null);

        AssistantToolCall toolCall = AssistantToolCall.builder()
            .toolCallRecordId("tcr-1")
            .toolCallId("call-1")
            .assistantMessageId("msg-assistant-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .toolName("get_time")
            .argumentsJson("{}")
            .callIndex(0)
            .status(ToolCallStatus.CREATED)
            .createdAt(Instant.now())
            .build();

        repository.saveToolCallCreated(toolCall);
        repository.updateToolCallStatus("tcr-1", ToolCallStatus.DISPATCHED);
        repository.updateToolCallStatus("tcr-1", ToolCallStatus.RUNNING);
        repository.updateToolCallStatus("tcr-1", ToolCallStatus.SUCCEEDED);

        verify(toolCallMapper, times(1)).insert(any(AgentMessageToolCallEntity.class));
        verify(toolCallMapper, times(3)).update(any(), any());
    }

    @Test
    void toolExecutionStatusShouldProgressByStage() {
        MysqlMessageRepository repository = new MysqlMessageRepository();

        AgentMessageToolCallMapper toolCallMapper = Mockito.mock(AgentMessageToolCallMapper.class);
        AgentToolExecutionMapper toolExecutionMapper = Mockito.mock(AgentToolExecutionMapper.class);
        setCoreFields(repository, toolCallMapper, toolExecutionMapper);

        when(toolExecutionMapper.selectOne(any())).thenReturn(null, new AgentToolExecutionEntity());

        ToolExecution runningExecution = ToolExecution.builder()
            .toolExecutionId("tex-1")
            .toolCallRecordId("tcr-1")
            .toolCallId("call-1")
            .toolResultMessageId(null)
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .toolName("get_time")
            .argumentsJson("{}")
            .status(ToolExecutionStatus.RUNNING)
            .startedAt(Instant.now())
            .createdAt(Instant.now())
            .build();
        ToolExecution succeededExecution = ToolExecution.builder()
            .toolExecutionId("tex-1")
            .toolCallRecordId("tcr-1")
            .toolCallId("call-1")
            .toolResultMessageId("msg-tool-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .toolName("get_time")
            .argumentsJson("{}")
            .outputText("{\"ok\":true}")
            .outputJson("{\"ok\":true}")
            .status(ToolExecutionStatus.SUCCEEDED)
            .durationMs(11L)
            .startedAt(Instant.now())
            .completedAt(Instant.now())
            .createdAt(Instant.now())
            .build();

        repository.upsertToolExecutionRunning(runningExecution);
        repository.updateToolExecutionFinal(succeededExecution);

        verify(toolExecutionMapper, times(1)).insert(any(AgentToolExecutionEntity.class));
        verify(toolExecutionMapper, times(1)).update(any(), any());
    }

    @Test
    void saveFailureToolFactsShouldInsertFailedCallAndExecutionWithNullResultMessageId() {
        MysqlMessageRepository repository = new MysqlMessageRepository();

        AgentMessageToolCallMapper toolCallMapper = Mockito.mock(AgentMessageToolCallMapper.class);
        AgentToolExecutionMapper toolExecutionMapper = Mockito.mock(AgentToolExecutionMapper.class);
        setCoreFields(repository, toolCallMapper, toolExecutionMapper);

        when(toolCallMapper.selectList(any())).thenReturn(List.of());
        when(toolExecutionMapper.selectList(any())).thenReturn(List.of());

        AssistantToolCall toolCall = AssistantToolCall.builder()
            .toolCallRecordId("tcr-1")
            .toolCallId("call-1")
            .assistantMessageId("msg-assistant-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .toolName("get_time")
            .argumentsJson("{}")
            .callIndex(0)
            .status(ToolCallStatus.FAILED)
            .createdAt(Instant.now())
            .build();
        ToolExecution toolExecution = ToolExecution.builder()
            .toolExecutionId("tex-1")
            .toolCallRecordId("tcr-1")
            .toolCallId("call-1")
            .toolResultMessageId(null)
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .toolName("get_time")
            .argumentsJson("{}")
            .status(ToolExecutionStatus.FAILED)
            .errorCode("TOOL_EXECUTION_FAILED")
            .errorMessage("tool failed")
            .durationMs(21L)
            .startedAt(Instant.now())
            .completedAt(Instant.now())
            .createdAt(Instant.now())
            .build();

        repository.saveFailureToolFacts(List.of(toolCall), List.of(toolExecution));

        ArgumentCaptor<AgentMessageToolCallEntity> toolCallCaptor = ArgumentCaptor.forClass(AgentMessageToolCallEntity.class);
        verify(toolCallMapper, times(1)).insert(toolCallCaptor.capture());
        assertEquals(ToolCallStatus.FAILED, toolCallCaptor.getValue().getStatus());
        assertNotNull(toolCallCaptor.getValue().getUpdatedAt());

        ArgumentCaptor<AgentToolExecutionEntity> executionCaptor = ArgumentCaptor.forClass(AgentToolExecutionEntity.class);
        verify(toolExecutionMapper, times(1)).insert(executionCaptor.capture());
        assertEquals(ToolExecutionStatus.FAILED, executionCaptor.getValue().getStatus());
        assertEquals("TOOL_EXECUTION_FAILED", executionCaptor.getValue().getErrorCode());
        assertEquals("tool failed", executionCaptor.getValue().getErrorMessage());
        assertEquals(21L, executionCaptor.getValue().getDurationMs());
        assertNotNull(executionCaptor.getValue().getCompletedAt());
        assertNotNull(executionCaptor.getValue().getUpdatedAt());
        assertNull(executionCaptor.getValue().getToolResultMessageId());
    }

    @Test
    void toolCallStatusShouldSupportCancelled() {
        MysqlMessageRepository repository = new MysqlMessageRepository();

        AgentMessageToolCallMapper toolCallMapper = Mockito.mock(AgentMessageToolCallMapper.class);
        setCoreFields(repository, toolCallMapper, Mockito.mock(AgentToolExecutionMapper.class));

        repository.updateToolCallStatus("tcr-cancel", ToolCallStatus.CANCELLED);

        verify(toolCallMapper, times(1)).update(any(), any());
    }

    @Test
    void saveFailureToolFactsShouldNotOverrideSucceededExecution() {
        MysqlMessageRepository repository = new MysqlMessageRepository();

        AgentMessageToolCallMapper toolCallMapper = Mockito.mock(AgentMessageToolCallMapper.class);
        AgentToolExecutionMapper toolExecutionMapper = Mockito.mock(AgentToolExecutionMapper.class);
        setCoreFields(repository, toolCallMapper, toolExecutionMapper);

        AgentMessageToolCallEntity existingSucceededCall = new AgentMessageToolCallEntity();
        existingSucceededCall.setToolCallRecordId("tcr-1");
        existingSucceededCall.setStatus(ToolCallStatus.SUCCEEDED);

        AgentToolExecutionEntity existingSucceededExecution = new AgentToolExecutionEntity();
        existingSucceededExecution.setToolCallRecordId("tcr-1");
        existingSucceededExecution.setStatus(ToolExecutionStatus.SUCCEEDED);

        when(toolCallMapper.selectList(any())).thenReturn(List.of(existingSucceededCall));
        when(toolExecutionMapper.selectList(any())).thenReturn(List.of(existingSucceededExecution));
        when(toolCallMapper.selectOne(any())).thenReturn(existingSucceededCall);
        when(toolExecutionMapper.selectOne(any())).thenReturn(existingSucceededExecution);

        AssistantToolCall toolCall = AssistantToolCall.builder()
            .toolCallRecordId("tcr-1")
            .toolCallId("call-1")
            .assistantMessageId("msg-assistant-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .toolName("get_time")
            .argumentsJson("{}")
            .callIndex(0)
            .status(ToolCallStatus.FAILED)
            .createdAt(Instant.now())
            .build();
        ToolExecution toolExecution = ToolExecution.builder()
            .toolExecutionId("tex-1")
            .toolCallRecordId("tcr-1")
            .toolCallId("call-1")
            .toolResultMessageId(null)
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .toolName("get_time")
            .argumentsJson("{}")
            .status(ToolExecutionStatus.FAILED)
            .errorCode("TOOL_EXECUTION_FAILED")
            .errorMessage("tool failed")
            .durationMs(21L)
            .startedAt(Instant.now())
            .completedAt(Instant.now())
            .createdAt(Instant.now())
            .build();

        repository.saveFailureToolFacts(List.of(toolCall), List.of(toolExecution));

        verify(toolCallMapper, times(1)).selectList(any());
        verify(toolExecutionMapper, times(1)).selectList(any());
        verify(toolCallMapper, times(1)).selectOne(any());
        verify(toolExecutionMapper, times(1)).selectOne(any());
        verifyNoMoreInteractions(toolCallMapper, toolExecutionMapper);
    }

    private static void setCoreFields(
        MysqlMessageRepository repository,
        AgentMessageToolCallMapper toolCallMapper,
        AgentToolExecutionMapper toolExecutionMapper
    ) {
        setField(repository, "messageMapper", Mockito.mock(AgentMessageMapper.class));
        setField(repository, "messageToolCallMapper", toolCallMapper);
        setField(repository, "toolExecutionMapper", toolExecutionMapper);
        setField(repository, "turnMapper", Mockito.mock(AgentTurnMapper.class));
        setField(repository, "handlerRegistry", Mockito.mock(MessageTypeHandlerRegistry.class));
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void initTableInfoIfAbsent(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        TableInfoHelper.initTableInfo(
            new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
            entityClass
        );
    }
}

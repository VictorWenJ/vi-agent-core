package com.vi.agent.core.infra.persistence.mysql.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.vi.agent.core.infra.persistence.message.handler.MessageTypeHandlerRegistry;
import com.vi.agent.core.infra.persistence.message.model.MessageWritePlan;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageToolCallEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentToolExecutionEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentMessageMapper;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentMessageToolCallMapper;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentToolExecutionMapper;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentTurnMapper;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.ToolMessage;
import com.vi.agent.core.model.tool.ToolExecution;
import com.vi.agent.core.model.tool.ToolCallStatus;
import com.vi.agent.core.model.tool.ToolExecutionStatus;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysqlMessageRepositoryToolStatusTest {

    @BeforeAll
    static void initTableMetadata() {
        initTableInfoIfAbsent(AgentMessageEntity.class);
        initTableInfoIfAbsent(AgentMessageToolCallEntity.class);
        initTableInfoIfAbsent(AgentToolExecutionEntity.class);
    }

    @Test
    void saveBatchShouldPersistToolStatusProgression() {
        MysqlMessageRepository repository = new MysqlMessageRepository();

        AgentMessageMapper messageMapper = Mockito.mock(AgentMessageMapper.class);
        AgentMessageToolCallMapper toolCallMapper = Mockito.mock(AgentMessageToolCallMapper.class);
        AgentToolExecutionMapper toolExecutionMapper = Mockito.mock(AgentToolExecutionMapper.class);
        AgentTurnMapper turnMapper = Mockito.mock(AgentTurnMapper.class);
        MessageTypeHandlerRegistry handlerRegistry = Mockito.mock(MessageTypeHandlerRegistry.class);

        setField(repository, "messageMapper", messageMapper);
        setField(repository, "messageToolCallMapper", toolCallMapper);
        setField(repository, "toolExecutionMapper", toolExecutionMapper);
        setField(repository, "turnMapper", turnMapper);
        setField(repository, "handlerRegistry", handlerRegistry);

        ToolMessage toolMessage = ToolMessage.create(
            "msg-tool-1",
            "conv-1",
            "sess-1",
            "turn-1",
            "run-1",
            2L,
            "ok",
            "tcr-1",
            "call-1",
            "get_time",
            ToolExecutionStatus.SUCCEEDED,
            null,
            null,
            5L,
            "{}"
        );

        AgentMessageEntity messageEntity = new AgentMessageEntity();
        messageEntity.setMessageId("msg-tool-1");

        AgentToolExecutionEntity executionEntity = new AgentToolExecutionEntity();
        executionEntity.setToolExecutionId("tex-msg-tool-1");
        executionEntity.setToolCallRecordId("tcr-1");
        executionEntity.setToolCallId("call-1");
        executionEntity.setToolResultMessageId("msg-tool-1");
        executionEntity.setConversationId("conv-1");
        executionEntity.setSessionId("sess-1");
        executionEntity.setTurnId("turn-1");
        executionEntity.setRunId("run-1");
        executionEntity.setToolName("get_time");
        executionEntity.setArgumentsJson("{}");
        executionEntity.setOutputText("ok");
        executionEntity.setOutputJson("ok");
        executionEntity.setStatus(ToolExecutionStatus.SUCCEEDED);
        executionEntity.setDurationMs(5L);
        executionEntity.setStartedAt(LocalDateTime.now());
        executionEntity.setCompletedAt(LocalDateTime.now());
        executionEntity.setCreatedAt(LocalDateTime.now());

        MessageWritePlan plan = MessageWritePlan.builder()
            .message(messageEntity)
            .toolCalls(List.of(buildToolCall("tcr-1", "call-1")))
            .toolExecution(executionEntity)
            .build();

        when(handlerRegistry.decompose(toolMessage)).thenReturn(plan);

        repository.saveBatch(List.of(toolMessage));

        verify(messageMapper, times(1)).insert(any(AgentMessageEntity.class));
        verify(toolCallMapper, times(1)).insert(any(AgentMessageToolCallEntity.class));
        verify(toolExecutionMapper, times(1)).insert(any(AgentToolExecutionEntity.class));
        verify(toolExecutionMapper, times(1)).update(any(), any(LambdaUpdateWrapper.class));

        ArgumentCaptor<LambdaUpdateWrapper<AgentMessageToolCallEntity>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(toolCallMapper, times(3)).update(any(), captor.capture());
        assertEquals(3, captor.getAllValues().size());
    }

    @Test
    void saveFailureToolFactsShouldInsertFailedCallAndExecution() {
        MysqlMessageRepository repository = new MysqlMessageRepository();

        AgentMessageMapper messageMapper = Mockito.mock(AgentMessageMapper.class);
        AgentMessageToolCallMapper toolCallMapper = Mockito.mock(AgentMessageToolCallMapper.class);
        AgentToolExecutionMapper toolExecutionMapper = Mockito.mock(AgentToolExecutionMapper.class);
        AgentTurnMapper turnMapper = Mockito.mock(AgentTurnMapper.class);
        MessageTypeHandlerRegistry handlerRegistry = Mockito.mock(MessageTypeHandlerRegistry.class);

        setField(repository, "messageMapper", messageMapper);
        setField(repository, "messageToolCallMapper", toolCallMapper);
        setField(repository, "toolExecutionMapper", toolExecutionMapper);
        setField(repository, "turnMapper", turnMapper);
        setField(repository, "handlerRegistry", handlerRegistry);

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
            .status(ToolCallStatus.CREATED)
            .createdAt(Instant.now())
            .build();
        ToolExecution toolExecution = ToolExecution.builder()
            .toolExecutionId("tex-1")
            .toolCallRecordId("tcr-1")
            .toolCallId("call-1")
            .toolResultMessageId("tex-1")
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
    }

    private static AgentMessageToolCallEntity buildToolCall(String recordId, String callId) {
        AgentMessageToolCallEntity entity = new AgentMessageToolCallEntity();
        entity.setToolCallRecordId(recordId);
        entity.setToolCallId(callId);
        entity.setAssistantMessageId("msg-assistant-1");
        entity.setConversationId("conv-1");
        entity.setSessionId("sess-1");
        entity.setTurnId("turn-1");
        entity.setRunId("run-1");
        entity.setToolName("get_time");
        entity.setArgumentsJson("{}");
        entity.setCallIndex(0);
        return entity;
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

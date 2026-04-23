package com.vi.agent.core.infra.persistence.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vi.agent.core.infra.persistence.message.handler.MessageTypeHandlerRegistry;
import com.vi.agent.core.infra.persistence.message.model.MessageAggregateRows;
import com.vi.agent.core.infra.persistence.message.model.MessageWritePlan;
import com.vi.agent.core.infra.persistence.mysql.convertor.MysqlTimeConvertor;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageToolCallEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentToolExecutionEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentTurnEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentMessageMapper;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentMessageToolCallMapper;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentToolExecutionMapper;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentTurnMapper;
import com.vi.agent.core.model.message.AssistantToolCall;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.MessageType;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.tool.ToolCallStatus;
import com.vi.agent.core.model.tool.ToolExecution;
import com.vi.agent.core.model.tool.ToolExecutionStatus;
import com.vi.agent.core.model.turn.TurnStatus;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MySQL message repository implementation.
 */
@Repository
public class MysqlMessageRepository implements MessageRepository {

    @Resource
    private AgentMessageMapper messageMapper;

    @Resource
    private AgentMessageToolCallMapper messageToolCallMapper;

    @Resource
    private AgentToolExecutionMapper toolExecutionMapper;

    @Resource
    private AgentTurnMapper turnMapper;

    @Resource
    private MessageTypeHandlerRegistry handlerRegistry;

    @Override
    public void saveBatch(List<Message> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return;
        }

        List<MessageWritePlan> writePlans = messages.stream()
            .filter(Objects::nonNull)
            .map(handlerRegistry::decompose)
            .toList();

        for (MessageWritePlan writePlan : writePlans) {
            if (writePlan.getMessage() != null) {
                messageMapper.insert(writePlan.getMessage());
            }
        }

        for (MessageWritePlan writePlan : writePlans) {
            if (!CollectionUtils.isEmpty(writePlan.getToolCalls())) {
                for (AgentMessageToolCallEntity toolCallEntity : writePlan.getToolCalls()) {
                    messageToolCallMapper.insert(toolCallEntity);
                }
            }
        }

        for (MessageWritePlan writePlan : writePlans) {
            if (writePlan.getToolExecution() != null) {
                persistToolExecutionProgress(writePlan.getToolExecution());
            }
        }
    }

    @Override
    public Message findByMessageId(String messageId) {
        AgentMessageEntity entity = messageMapper.selectOne(
            Wrappers.lambdaQuery(AgentMessageEntity.class)
                .eq(AgentMessageEntity::getMessageId, messageId)
                .last("limit 1")
        );
        if (entity == null) {
            return null;
        }
        return handlerRegistry.assemble(buildAggregateRow(entity));
    }

    @Override
    public List<Message> findCompletedContextBySessionId(String sessionId, int maxTurns) {
        List<AgentTurnEntity> completedTurns = turnMapper.selectList(
            Wrappers.lambdaQuery(AgentTurnEntity.class)
                .eq(AgentTurnEntity::getSessionId, sessionId)
                .eq(AgentTurnEntity::getStatus, TurnStatus.COMPLETED)
                .orderByDesc(AgentTurnEntity::getCreatedAt));
        if (CollectionUtils.isEmpty(completedTurns)) {
            return Collections.emptyList();
        }

        List<String> selectedTurnIds = completedTurns.stream()
            .map(AgentTurnEntity::getTurnId)
            .filter(StringUtils::isNotBlank)
            .toList();
        if (CollectionUtils.isEmpty(selectedTurnIds)) {
            return Collections.emptyList();
        }

        //按轮数截取
        if (maxTurns > 0 && selectedTurnIds.size() > maxTurns) {
            selectedTurnIds = selectedTurnIds.subList(0, maxTurns);
        }

        Set<String> turnIdSet = new HashSet<>(selectedTurnIds);
        List<AgentMessageEntity> entities = messageMapper.selectList(
            Wrappers.lambdaQuery(AgentMessageEntity.class)
                .eq(AgentMessageEntity::getSessionId, sessionId)
                .in(AgentMessageEntity::getTurnId, turnIdSet)
                .orderByAsc(AgentMessageEntity::getSequenceNo)
        );
        if (CollectionUtils.isEmpty(entities)) {
            return Collections.emptyList();
        }

        Map<String, List<AgentMessageToolCallEntity>> toolCallsByAssistantMessageId = loadToolCallsByAssistantMessageId(entities);
        Map<String, AgentToolExecutionEntity> executionsByToolMessageId = loadToolExecutionsByToolMessageId(entities);

        List<Message> results = new ArrayList<>(entities.size());
        for (AgentMessageEntity entity : entities) {
            MessageAggregateRows aggregateRows = MessageAggregateRows.builder()
                .message(entity)
                .toolCalls(toolCallsByAssistantMessageId.getOrDefault(entity.getMessageId(), Collections.emptyList()))
                .toolExecution(executionsByToolMessageId.get(entity.getMessageId()))
                .build();
            results.add(handlerRegistry.assemble(aggregateRows));
        }
        return results;
    }

    @Override
    public List<Message> findByTurnId(String turnId) {
        List<AgentMessageEntity> entities = messageMapper.selectList(
            Wrappers.lambdaQuery(AgentMessageEntity.class)
                .eq(AgentMessageEntity::getTurnId, turnId)
                .orderByAsc(AgentMessageEntity::getSequenceNo)
        );
        if (CollectionUtils.isEmpty(entities)) {
            return Collections.emptyList();
        }
        Map<String, List<AgentMessageToolCallEntity>> toolCallsByAssistantMessageId = loadToolCallsByAssistantMessageId(entities);
        Map<String, AgentToolExecutionEntity> executionsByToolMessageId = loadToolExecutionsByToolMessageId(entities);
        return entities.stream()
            .map(entity -> MessageAggregateRows.builder()
                .message(entity)
                .toolCalls(toolCallsByAssistantMessageId.getOrDefault(entity.getMessageId(), Collections.emptyList()))
                .toolExecution(executionsByToolMessageId.get(entity.getMessageId()))
                .build())
            .map(handlerRegistry::assemble)
            .toList();
    }

    @Override
    public Message findFinalAssistantMessageByTurnId(String turnId) {
        AgentMessageEntity entity = messageMapper.selectOne(
            Wrappers.lambdaQuery(AgentMessageEntity.class)
                .eq(AgentMessageEntity::getTurnId, turnId)
                .eq(AgentMessageEntity::getRole, MessageRole.ASSISTANT)
                .eq(AgentMessageEntity::getMessageType, MessageType.ASSISTANT_OUTPUT)
                .orderByDesc(AgentMessageEntity::getSequenceNo)
                .last("limit 1")
        );
        if (entity == null) {
            return null;
        }
        return handlerRegistry.assemble(buildAggregateRow(entity));
    }

    @Override
    public long nextSequenceNo(String sessionId) {
        AgentMessageEntity entity = messageMapper.selectOne(
            Wrappers.lambdaQuery(AgentMessageEntity.class)
                .eq(AgentMessageEntity::getSessionId, sessionId)
                .orderByDesc(AgentMessageEntity::getSequenceNo)
                .last("limit 1")
        );
        if (entity == null || entity.getSequenceNo() == null) {
            return 1L;
        }
        return entity.getSequenceNo() + 1;
    }

    @Override
    public void saveFailureToolFacts(List<AssistantToolCall> toolCalls, List<ToolExecution> toolExecutions) {
        upsertFailedToolCalls(toolCalls);
        upsertFailedToolExecutions(toolExecutions);
    }

    private void upsertFailedToolCalls(List<AssistantToolCall> toolCalls) {
        if (CollectionUtils.isEmpty(toolCalls)) {
            return;
        }
        Map<String, AssistantToolCall> uniqueByRecordId = toolCalls.stream()
            .filter(Objects::nonNull)
            .filter(toolCall -> StringUtils.isNotBlank(toolCall.getToolCallRecordId()))
            .collect(Collectors.toMap(AssistantToolCall::getToolCallRecordId, toolCall -> toolCall, (left, right) -> right, LinkedHashMap::new));
        if (uniqueByRecordId.isEmpty()) {
            return;
        }

        List<AgentMessageToolCallEntity> existingEntities = messageToolCallMapper.selectList(
            Wrappers.lambdaQuery(AgentMessageToolCallEntity.class)
                .in(AgentMessageToolCallEntity::getToolCallRecordId, uniqueByRecordId.keySet())
        );
        Set<String> existingRecordIds = CollectionUtils.isEmpty(existingEntities)
            ? Set.of()
            : existingEntities.stream()
                .map(AgentMessageToolCallEntity::getToolCallRecordId)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());

        for (AssistantToolCall toolCall : uniqueByRecordId.values()) {
            String toolCallRecordId = toolCall.getToolCallRecordId();
            if (existingRecordIds.contains(toolCallRecordId)) {
                updateToolCallStatus(toolCallRecordId, ToolCallStatus.FAILED);
                continue;
            }
            messageToolCallMapper.insert(toFailedToolCallEntity(toolCall));
        }
    }

    private void upsertFailedToolExecutions(List<ToolExecution> toolExecutions) {
        if (CollectionUtils.isEmpty(toolExecutions)) {
            return;
        }
        Map<String, ToolExecution> uniqueByRecordId = toolExecutions.stream()
            .filter(Objects::nonNull)
            .filter(toolExecution -> StringUtils.isNotBlank(toolExecution.getToolCallRecordId()))
            .collect(Collectors.toMap(ToolExecution::getToolCallRecordId, toolExecution -> toolExecution, (left, right) -> right, LinkedHashMap::new));
        if (uniqueByRecordId.isEmpty()) {
            return;
        }

        List<AgentToolExecutionEntity> existingEntities = toolExecutionMapper.selectList(
            Wrappers.lambdaQuery(AgentToolExecutionEntity.class)
                .in(AgentToolExecutionEntity::getToolCallRecordId, uniqueByRecordId.keySet())
        );
        Set<String> existingRecordIds = CollectionUtils.isEmpty(existingEntities)
            ? Set.of()
            : existingEntities.stream()
                .map(AgentToolExecutionEntity::getToolCallRecordId)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());

        for (ToolExecution toolExecution : uniqueByRecordId.values()) {
            String toolCallRecordId = toolExecution.getToolCallRecordId();
            if (existingRecordIds.contains(toolCallRecordId)) {
                updateFailedToolExecution(toolExecution);
                continue;
            }
            toolExecutionMapper.insert(toFailedToolExecutionEntity(toolExecution));
        }
    }

    private void persistToolExecutionProgress(AgentToolExecutionEntity executionEntity) {
        String toolCallRecordId = executionEntity.getToolCallRecordId();
        if (StringUtils.isNotBlank(toolCallRecordId)) {
            updateToolCallStatus(toolCallRecordId, ToolCallStatus.DISPATCHED);
            updateToolCallStatus(toolCallRecordId, ToolCallStatus.RUNNING);
        }

        AgentToolExecutionEntity runningExecution = toRunningExecution(executionEntity);
        toolExecutionMapper.insert(runningExecution);

        ToolExecutionStatus finalStatus = executionEntity.getStatus() == ToolExecutionStatus.FAILED
            ? ToolExecutionStatus.FAILED
            : ToolExecutionStatus.SUCCEEDED;

        toolExecutionMapper.update(
            null,
            Wrappers.lambdaUpdate(AgentToolExecutionEntity.class)
                .eq(AgentToolExecutionEntity::getToolExecutionId, executionEntity.getToolExecutionId())
                .set(AgentToolExecutionEntity::getStatus, finalStatus)
                .set(AgentToolExecutionEntity::getOutputText, executionEntity.getOutputText())
                .set(AgentToolExecutionEntity::getOutputJson, executionEntity.getOutputJson())
                .set(AgentToolExecutionEntity::getErrorCode, executionEntity.getErrorCode())
                .set(AgentToolExecutionEntity::getErrorMessage, executionEntity.getErrorMessage())
                .set(AgentToolExecutionEntity::getDurationMs, executionEntity.getDurationMs())
                .set(AgentToolExecutionEntity::getCompletedAt, executionEntity.getCompletedAt())
                .set(AgentToolExecutionEntity::getUpdatedAt, LocalDateTime.now())
        );

        if (StringUtils.isNotBlank(toolCallRecordId)) {
            updateToolCallStatus(
                toolCallRecordId,
                finalStatus == ToolExecutionStatus.SUCCEEDED ? ToolCallStatus.SUCCEEDED : ToolCallStatus.FAILED
            );
        }
    }

    private AgentToolExecutionEntity toRunningExecution(AgentToolExecutionEntity source) {
        AgentToolExecutionEntity target = new AgentToolExecutionEntity();
        target.setToolExecutionId(source.getToolExecutionId());
        target.setToolCallRecordId(source.getToolCallRecordId());
        target.setToolCallId(source.getToolCallId());
        target.setToolResultMessageId(source.getToolResultMessageId());
        target.setConversationId(source.getConversationId());
        target.setSessionId(source.getSessionId());
        target.setTurnId(source.getTurnId());
        target.setRunId(source.getRunId());
        target.setToolName(source.getToolName());
        target.setArgumentsJson(source.getArgumentsJson());
        target.setOutputText(null);
        target.setOutputJson(null);
        target.setStatus(ToolExecutionStatus.RUNNING);
        target.setErrorCode(null);
        target.setErrorMessage(null);
        target.setDurationMs(null);
        target.setStartedAt(source.getStartedAt() == null ? LocalDateTime.now() : source.getStartedAt());
        target.setCompletedAt(null);
        target.setCreatedAt(source.getCreatedAt() == null ? LocalDateTime.now() : source.getCreatedAt());
        target.setUpdatedAt(LocalDateTime.now());
        return target;
    }

    private void updateToolCallStatus(String toolCallRecordId, ToolCallStatus status) {
        messageToolCallMapper.update(
            null,
            Wrappers.lambdaUpdate(AgentMessageToolCallEntity.class)
                .eq(AgentMessageToolCallEntity::getToolCallRecordId, toolCallRecordId)
                .set(AgentMessageToolCallEntity::getStatus, status)
                .set(AgentMessageToolCallEntity::getUpdatedAt, LocalDateTime.now())
        );
    }

    private void updateFailedToolExecution(ToolExecution toolExecution) {
        toolExecutionMapper.update(
            null,
            Wrappers.lambdaUpdate(AgentToolExecutionEntity.class)
                .eq(AgentToolExecutionEntity::getToolCallRecordId, toolExecution.getToolCallRecordId())
                .set(AgentToolExecutionEntity::getStatus, ToolExecutionStatus.FAILED)
                .set(AgentToolExecutionEntity::getErrorCode, StringUtils.defaultIfBlank(toolExecution.getErrorCode(), "TOOL_EXECUTION_FAILED"))
                .set(AgentToolExecutionEntity::getErrorMessage, StringUtils.defaultIfBlank(toolExecution.getErrorMessage(), "tool execution failed"))
                .set(AgentToolExecutionEntity::getDurationMs, toolExecution.getDurationMs())
                .set(AgentToolExecutionEntity::getOutputText, toolExecution.getOutputText())
                .set(AgentToolExecutionEntity::getOutputJson, toolExecution.getOutputJson())
                .set(AgentToolExecutionEntity::getCompletedAt, defaultNow(toolExecution.getCompletedAt()))
                .set(AgentToolExecutionEntity::getUpdatedAt, LocalDateTime.now())
        );
    }

    private AgentMessageToolCallEntity toFailedToolCallEntity(AssistantToolCall toolCall) {
        LocalDateTime now = LocalDateTime.now();
        AgentMessageToolCallEntity entity = new AgentMessageToolCallEntity();
        entity.setToolCallRecordId(toolCall.getToolCallRecordId());
        entity.setToolCallId(toolCall.getToolCallId());
        entity.setAssistantMessageId(toolCall.getAssistantMessageId());
        entity.setConversationId(toolCall.getConversationId());
        entity.setSessionId(toolCall.getSessionId());
        entity.setTurnId(toolCall.getTurnId());
        entity.setRunId(toolCall.getRunId());
        entity.setToolName(toolCall.getToolName());
        entity.setArgumentsJson(toolCall.getArgumentsJson());
        entity.setCallIndex(toolCall.getCallIndex());
        entity.setStatus(ToolCallStatus.FAILED);
        entity.setCreatedAt(defaultNow(toolCall.getCreatedAt()));
        entity.setUpdatedAt(now);
        return entity;
    }

    private AgentToolExecutionEntity toFailedToolExecutionEntity(ToolExecution toolExecution) {
        LocalDateTime now = LocalDateTime.now();
        AgentToolExecutionEntity entity = new AgentToolExecutionEntity();
        entity.setToolExecutionId(StringUtils.defaultIfBlank(toolExecution.getToolExecutionId(), "tex-" + UUID.randomUUID()));
        entity.setToolCallRecordId(toolExecution.getToolCallRecordId());
        entity.setToolCallId(toolExecution.getToolCallId());
        entity.setToolResultMessageId(resolveToolResultMessageId(toolExecution));
        entity.setConversationId(toolExecution.getConversationId());
        entity.setSessionId(toolExecution.getSessionId());
        entity.setTurnId(toolExecution.getTurnId());
        entity.setRunId(toolExecution.getRunId());
        entity.setToolName(toolExecution.getToolName());
        entity.setArgumentsJson(toolExecution.getArgumentsJson());
        entity.setOutputText(toolExecution.getOutputText());
        entity.setOutputJson(toolExecution.getOutputJson());
        entity.setStatus(ToolExecutionStatus.FAILED);
        entity.setErrorCode(StringUtils.defaultIfBlank(toolExecution.getErrorCode(), "TOOL_EXECUTION_FAILED"));
        entity.setErrorMessage(StringUtils.defaultIfBlank(toolExecution.getErrorMessage(), "tool execution failed"));
        entity.setDurationMs(toolExecution.getDurationMs());
        entity.setStartedAt(defaultNow(toolExecution.getStartedAt()));
        entity.setCompletedAt(defaultNow(toolExecution.getCompletedAt()));
        entity.setCreatedAt(defaultNow(toolExecution.getCreatedAt()));
        entity.setUpdatedAt(now);
        return entity;
    }

    private String resolveToolResultMessageId(ToolExecution toolExecution) {
        if (StringUtils.isNotBlank(toolExecution.getToolResultMessageId())) {
            return StringUtils.left(toolExecution.getToolResultMessageId(), 64);
        }
        String fallback = StringUtils.defaultIfBlank(toolExecution.getToolExecutionId(), toolExecution.getToolCallRecordId());
        return StringUtils.left("failed-" + fallback, 64);
    }

    private LocalDateTime defaultNow(java.time.Instant instant) {
        LocalDateTime converted = MysqlTimeConvertor.toLocalDateTime(instant);
        return converted == null ? LocalDateTime.now() : converted;
    }

    private MessageAggregateRows buildAggregateRow(AgentMessageEntity messageEntity) {
        List<AgentMessageToolCallEntity> toolCalls = messageToolCallMapper.selectList(
            Wrappers.lambdaQuery(AgentMessageToolCallEntity.class)
                .eq(AgentMessageToolCallEntity::getAssistantMessageId, messageEntity.getMessageId())
                .orderByAsc(AgentMessageToolCallEntity::getCallIndex)
        );

        AgentToolExecutionEntity toolExecution = toolExecutionMapper.selectOne(
            Wrappers.lambdaQuery(AgentToolExecutionEntity.class)
                .eq(AgentToolExecutionEntity::getToolResultMessageId, messageEntity.getMessageId())
                .last("limit 1")
        );

        return MessageAggregateRows.builder()
            .message(messageEntity)
            .toolCalls(toolCalls)
            .toolExecution(toolExecution)
            .build();
    }

    private Map<String, List<AgentMessageToolCallEntity>> loadToolCallsByAssistantMessageId(List<AgentMessageEntity> entities) {
        List<String> assistantMessageIds = entities.stream()
            .map(AgentMessageEntity::getMessageId)
            .toList();
        if (CollectionUtils.isEmpty(assistantMessageIds)) {
            return Map.of();
        }
        List<AgentMessageToolCallEntity> toolCallEntities = messageToolCallMapper.selectList(
            Wrappers.lambdaQuery(AgentMessageToolCallEntity.class)
                .in(AgentMessageToolCallEntity::getAssistantMessageId, assistantMessageIds)
                .orderByAsc(AgentMessageToolCallEntity::getCallIndex)
        );
        if (CollectionUtils.isEmpty(toolCallEntities)) {
            return Map.of();
        }
        return toolCallEntities.stream().collect(Collectors.groupingBy(AgentMessageToolCallEntity::getAssistantMessageId));
    }

    private Map<String, AgentToolExecutionEntity> loadToolExecutionsByToolMessageId(List<AgentMessageEntity> entities) {
        List<String> messageIds = entities.stream().map(AgentMessageEntity::getMessageId).toList();
        if (CollectionUtils.isEmpty(messageIds)) {
            return Map.of();
        }
        List<AgentToolExecutionEntity> executionEntities = toolExecutionMapper.selectList(
            Wrappers.lambdaQuery(AgentToolExecutionEntity.class)
                .in(AgentToolExecutionEntity::getToolResultMessageId, messageIds)
        );
        if (CollectionUtils.isEmpty(executionEntities)) {
            return Map.of();
        }
        Map<String, AgentToolExecutionEntity> result = new HashMap<>();
        for (AgentToolExecutionEntity executionEntity : executionEntities) {
            result.put(executionEntity.getToolResultMessageId(), executionEntity);
        }
        return result;
    }
}

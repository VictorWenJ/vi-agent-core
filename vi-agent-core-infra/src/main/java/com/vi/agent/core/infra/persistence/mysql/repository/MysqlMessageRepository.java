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
import com.vi.agent.core.model.message.AssistantMessage;
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
                insertMessageIfAbsent(writePlan.getMessage());
            }
        }

        for (MessageWritePlan writePlan : writePlans) {
            if (!CollectionUtils.isEmpty(writePlan.getToolCalls())) {
                for (AgentMessageToolCallEntity toolCallEntity : writePlan.getToolCalls()) {
                    insertToolCallIfAbsent(toolCallEntity);
                }
            }
        }

        for (MessageWritePlan writePlan : writePlans) {
            if (writePlan.getToolExecution() != null) {
                upsertToolExecutionFinal(writePlan.getToolExecution());
            }
        }
    }

    @Override
    public Optional<Message> findByMessageId(String messageId) {
        AgentMessageEntity entity = messageMapper.selectOne(
            Wrappers.lambdaQuery(AgentMessageEntity.class)
                .eq(AgentMessageEntity::getMessageId, messageId)
                .last("limit 1")
        );
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlerRegistry.assemble(buildAggregateRow(entity)));
    }

    /**
     * 1、查询当前sessionId下完成的turn
     * 2、按maxTurns截取
     * 3、组装tool call和executions数据
     * 4、返回完整带有tool call和executions的总数居
     * @param sessionId
     * @param maxTurns
     * @return
     */
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
    public Optional<Message> findFinalAssistantMessageByTurnId(String turnId) {
        AgentMessageEntity entity = messageMapper.selectOne(
            Wrappers.lambdaQuery(AgentMessageEntity.class)
                .eq(AgentMessageEntity::getTurnId, turnId)
                .eq(AgentMessageEntity::getRole, MessageRole.ASSISTANT)
                .eq(AgentMessageEntity::getMessageType, MessageType.ASSISTANT_OUTPUT)
                .orderByDesc(AgentMessageEntity::getSequenceNo)
                .last("limit 1")
        );
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(handlerRegistry.assemble(buildAggregateRow(entity)));
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
    public void saveAssistantMessageIfAbsent(AssistantMessage assistantMessage) {
        if (assistantMessage == null || StringUtils.isBlank(assistantMessage.getMessageId())) {
            return;
        }
        AgentMessageEntity existing = messageMapper.selectOne(
            Wrappers.lambdaQuery(AgentMessageEntity.class)
                .eq(AgentMessageEntity::getMessageId, assistantMessage.getMessageId())
                .last("limit 1")
        );
        if (existing != null) {
            return;
        }
        MessageWritePlan writePlan = handlerRegistry.decompose(assistantMessage);
        if (writePlan.getMessage() != null) {
            messageMapper.insert(writePlan.getMessage());
        }
    }

    @Override
    public void saveToolCallCreated(AssistantToolCall toolCall) {
        if (toolCall == null || StringUtils.isBlank(toolCall.getToolCallRecordId())) {
            return;
        }
        AgentMessageToolCallEntity existing = findToolCallByRecordId(toolCall.getToolCallRecordId());
        if (existing == null) {
            messageToolCallMapper.insert(toToolCallEntity(toolCall, ToolCallStatus.CREATED));
        }
    }

    @Override
    public void updateToolCallStatus(String toolCallRecordId, ToolCallStatus status) {
        if (StringUtils.isBlank(toolCallRecordId) || status == null) {
            return;
        }
        messageToolCallMapper.update(
            null,
            Wrappers.lambdaUpdate(AgentMessageToolCallEntity.class)
                .eq(AgentMessageToolCallEntity::getToolCallRecordId, toolCallRecordId)
                .set(AgentMessageToolCallEntity::getStatus, status)
                .set(AgentMessageToolCallEntity::getUpdatedAt, LocalDateTime.now())
        );
    }

    @Override
    public void upsertToolExecutionRunning(ToolExecution toolExecution) {
        if (toolExecution == null || StringUtils.isBlank(toolExecution.getToolCallRecordId())) {
            return;
        }
        AgentToolExecutionEntity existing = findToolExecutionByRecordId(toolExecution.getToolCallRecordId());
        if (existing == null) {
            toolExecutionMapper.insert(toRunningExecutionEntity(toolExecution));
            return;
        }
        toolExecutionMapper.update(
            null,
            Wrappers.lambdaUpdate(AgentToolExecutionEntity.class)
                .eq(AgentToolExecutionEntity::getToolCallRecordId, toolExecution.getToolCallRecordId())
                .set(AgentToolExecutionEntity::getStatus, ToolExecutionStatus.RUNNING)
                .set(AgentToolExecutionEntity::getToolExecutionId, StringUtils.defaultIfBlank(toolExecution.getToolExecutionId(), existing.getToolExecutionId()))
                .set(AgentToolExecutionEntity::getStartedAt, defaultNow(toolExecution.getStartedAt()))
                .set(AgentToolExecutionEntity::getCompletedAt, null)
                .set(AgentToolExecutionEntity::getErrorCode, null)
                .set(AgentToolExecutionEntity::getErrorMessage, null)
                .set(AgentToolExecutionEntity::getDurationMs, null)
                .set(AgentToolExecutionEntity::getOutputText, null)
                .set(AgentToolExecutionEntity::getOutputJson, null)
                .set(AgentToolExecutionEntity::getToolResultMessageId, null)
                .set(AgentToolExecutionEntity::getUpdatedAt, LocalDateTime.now())
        );
    }

    @Override
    public void updateToolExecutionFinal(ToolExecution toolExecution) {
        if (toolExecution == null || StringUtils.isBlank(toolExecution.getToolCallRecordId())) {
            return;
        }
        AgentToolExecutionEntity existing = findToolExecutionByRecordId(toolExecution.getToolCallRecordId());
        if (existing == null) {
            toolExecutionMapper.insert(toFinalExecutionEntity(toolExecution));
            return;
        }
        toolExecutionMapper.update(
            null,
            Wrappers.lambdaUpdate(AgentToolExecutionEntity.class)
                .eq(AgentToolExecutionEntity::getToolCallRecordId, toolExecution.getToolCallRecordId())
                .set(AgentToolExecutionEntity::getStatus, toolExecution.getStatus())
                .set(AgentToolExecutionEntity::getToolResultMessageId, toolExecution.getToolResultMessageId())
                .set(AgentToolExecutionEntity::getOutputText, toolExecution.getOutputText())
                .set(AgentToolExecutionEntity::getOutputJson, toolExecution.getOutputJson())
                .set(AgentToolExecutionEntity::getErrorCode, toolExecution.getErrorCode())
                .set(AgentToolExecutionEntity::getErrorMessage, toolExecution.getErrorMessage())
                .set(AgentToolExecutionEntity::getDurationMs, toolExecution.getDurationMs())
                .set(AgentToolExecutionEntity::getCompletedAt, defaultNow(toolExecution.getCompletedAt()))
                .set(AgentToolExecutionEntity::getUpdatedAt, LocalDateTime.now())
        );
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
            ToolCallStatus targetStatus = toolCall.getStatus() == null ? ToolCallStatus.FAILED : toolCall.getStatus();
            if (existingRecordIds.contains(toolCallRecordId)) {
                AgentMessageToolCallEntity existing = findToolCallByRecordId(toolCallRecordId);
                if (existing != null && shouldSkipToolCallOverride(existing.getStatus(), targetStatus)) {
                    continue;
                }
                updateToolCallStatus(toolCallRecordId, targetStatus);
                continue;
            }
            messageToolCallMapper.insert(toToolCallEntity(toolCall, targetStatus));
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
                AgentToolExecutionEntity existing = findToolExecutionByRecordId(toolCallRecordId);
                if (existing != null && shouldSkipToolExecutionOverride(existing.getStatus(), toolExecution.getStatus())) {
                    continue;
                }
                updateToolExecutionByFact(toolExecution);
                continue;
            }
            if (toolExecution.getStatus() == ToolExecutionStatus.RUNNING) {
                toolExecutionMapper.insert(toRunningExecutionEntity(toolExecution));
            } else {
                toolExecutionMapper.insert(toFinalExecutionEntity(toolExecution));
            }
        }
    }

    private void upsertToolExecutionFinal(AgentToolExecutionEntity executionEntity) {
        if (executionEntity == null || StringUtils.isBlank(executionEntity.getToolCallRecordId())) {
            return;
        }
        AgentToolExecutionEntity existing = findToolExecutionByRecordId(executionEntity.getToolCallRecordId());
        if (existing == null) {
            toolExecutionMapper.insert(executionEntity);
        } else {
            toolExecutionMapper.update(
                null,
                Wrappers.lambdaUpdate(AgentToolExecutionEntity.class)
                    .eq(AgentToolExecutionEntity::getToolCallRecordId, executionEntity.getToolCallRecordId())
                    .set(AgentToolExecutionEntity::getStatus, executionEntity.getStatus())
                    .set(AgentToolExecutionEntity::getToolResultMessageId, executionEntity.getToolResultMessageId())
                    .set(AgentToolExecutionEntity::getOutputText, executionEntity.getOutputText())
                    .set(AgentToolExecutionEntity::getOutputJson, executionEntity.getOutputJson())
                    .set(AgentToolExecutionEntity::getErrorCode, executionEntity.getErrorCode())
                    .set(AgentToolExecutionEntity::getErrorMessage, executionEntity.getErrorMessage())
                    .set(AgentToolExecutionEntity::getDurationMs, executionEntity.getDurationMs())
                    .set(AgentToolExecutionEntity::getCompletedAt, executionEntity.getCompletedAt())
                    .set(AgentToolExecutionEntity::getUpdatedAt, LocalDateTime.now())
            );
        }
        if (StringUtils.isNotBlank(executionEntity.getToolCallRecordId())) {
            updateToolCallStatus(
                executionEntity.getToolCallRecordId(),
                executionEntity.getStatus() == ToolExecutionStatus.SUCCEEDED ? ToolCallStatus.SUCCEEDED : ToolCallStatus.FAILED
            );
        }
    }

    private void updateToolExecutionByFact(ToolExecution toolExecution) {
        ToolExecutionStatus targetStatus = toolExecution.getStatus() == null ? ToolExecutionStatus.FAILED : toolExecution.getStatus();
        toolExecutionMapper.update(
            null,
            Wrappers.lambdaUpdate(AgentToolExecutionEntity.class)
                .eq(AgentToolExecutionEntity::getToolCallRecordId, toolExecution.getToolCallRecordId())
                .set(AgentToolExecutionEntity::getStatus, targetStatus)
                .set(AgentToolExecutionEntity::getToolResultMessageId, targetStatus == ToolExecutionStatus.SUCCEEDED
                    ? toolExecution.getToolResultMessageId() : null)
                .set(AgentToolExecutionEntity::getErrorCode, targetStatus == ToolExecutionStatus.FAILED
                    ? StringUtils.defaultIfBlank(toolExecution.getErrorCode(), "TOOL_EXECUTION_FAILED") : null)
                .set(AgentToolExecutionEntity::getErrorMessage, targetStatus == ToolExecutionStatus.FAILED
                    ? StringUtils.defaultIfBlank(toolExecution.getErrorMessage(), "tool execution failed") : null)
                .set(AgentToolExecutionEntity::getDurationMs, toolExecution.getDurationMs())
                .set(AgentToolExecutionEntity::getOutputText, toolExecution.getOutputText())
                .set(AgentToolExecutionEntity::getOutputJson, toolExecution.getOutputJson())
                .set(AgentToolExecutionEntity::getCompletedAt, targetStatus == ToolExecutionStatus.RUNNING ? null : defaultNow(toolExecution.getCompletedAt()))
                .set(AgentToolExecutionEntity::getUpdatedAt, LocalDateTime.now())
        );
    }

    private void insertToolCallIfAbsent(AgentMessageToolCallEntity toolCallEntity) {
        if (toolCallEntity == null || StringUtils.isBlank(toolCallEntity.getToolCallRecordId())) {
            return;
        }
        AgentMessageToolCallEntity existing = findToolCallByRecordId(toolCallEntity.getToolCallRecordId());
        if (existing == null) {
            messageToolCallMapper.insert(toolCallEntity);
        }
    }

    private AgentMessageToolCallEntity findToolCallByRecordId(String toolCallRecordId) {
        if (StringUtils.isBlank(toolCallRecordId)) {
            return null;
        }
        return messageToolCallMapper.selectOne(
            Wrappers.lambdaQuery(AgentMessageToolCallEntity.class)
                .eq(AgentMessageToolCallEntity::getToolCallRecordId, toolCallRecordId)
                .last("limit 1")
        );
    }

    private AgentToolExecutionEntity findToolExecutionByRecordId(String toolCallRecordId) {
        if (StringUtils.isBlank(toolCallRecordId)) {
            return null;
        }
        return toolExecutionMapper.selectOne(
            Wrappers.lambdaQuery(AgentToolExecutionEntity.class)
                .eq(AgentToolExecutionEntity::getToolCallRecordId, toolCallRecordId)
                .last("limit 1")
        );
    }

    private AgentMessageToolCallEntity toToolCallEntity(AssistantToolCall toolCall, ToolCallStatus status) {
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
        entity.setStatus(status);
        entity.setCreatedAt(defaultNow(toolCall.getCreatedAt()));
        entity.setUpdatedAt(now);
        return entity;
    }

    private AgentToolExecutionEntity toRunningExecutionEntity(ToolExecution toolExecution) {
        LocalDateTime now = LocalDateTime.now();
        AgentToolExecutionEntity entity = new AgentToolExecutionEntity();
        entity.setToolExecutionId(StringUtils.defaultIfBlank(toolExecution.getToolExecutionId(), "tex-" + UUID.randomUUID()));
        entity.setToolCallRecordId(toolExecution.getToolCallRecordId());
        entity.setToolCallId(toolExecution.getToolCallId());
        entity.setToolResultMessageId(null);
        entity.setConversationId(toolExecution.getConversationId());
        entity.setSessionId(toolExecution.getSessionId());
        entity.setTurnId(toolExecution.getTurnId());
        entity.setRunId(toolExecution.getRunId());
        entity.setToolName(toolExecution.getToolName());
        entity.setArgumentsJson(toolExecution.getArgumentsJson());
        entity.setOutputText(null);
        entity.setOutputJson(null);
        entity.setStatus(ToolExecutionStatus.RUNNING);
        entity.setErrorCode(null);
        entity.setErrorMessage(null);
        entity.setDurationMs(null);
        entity.setStartedAt(defaultNow(toolExecution.getStartedAt()));
        entity.setCompletedAt(null);
        entity.setCreatedAt(defaultNow(toolExecution.getCreatedAt()));
        entity.setUpdatedAt(now);
        return entity;
    }

    private AgentToolExecutionEntity toFinalExecutionEntity(ToolExecution toolExecution) {
        LocalDateTime now = LocalDateTime.now();
        AgentToolExecutionEntity entity = new AgentToolExecutionEntity();
        entity.setToolExecutionId(StringUtils.defaultIfBlank(toolExecution.getToolExecutionId(), "tex-" + UUID.randomUUID()));
        entity.setToolCallRecordId(toolExecution.getToolCallRecordId());
        entity.setToolCallId(toolExecution.getToolCallId());
        entity.setToolResultMessageId(toolExecution.getStatus() == ToolExecutionStatus.SUCCEEDED
            ? toolExecution.getToolResultMessageId() : null);
        entity.setConversationId(toolExecution.getConversationId());
        entity.setSessionId(toolExecution.getSessionId());
        entity.setTurnId(toolExecution.getTurnId());
        entity.setRunId(toolExecution.getRunId());
        entity.setToolName(toolExecution.getToolName());
        entity.setArgumentsJson(toolExecution.getArgumentsJson());
        entity.setOutputText(toolExecution.getOutputText());
        entity.setOutputJson(toolExecution.getOutputJson());
        entity.setStatus(toolExecution.getStatus());
        entity.setErrorCode(toolExecution.getStatus() == ToolExecutionStatus.FAILED
            ? StringUtils.defaultIfBlank(toolExecution.getErrorCode(), "TOOL_EXECUTION_FAILED") : null);
        entity.setErrorMessage(toolExecution.getStatus() == ToolExecutionStatus.FAILED
            ? StringUtils.defaultIfBlank(toolExecution.getErrorMessage(), "tool execution failed") : null);
        entity.setDurationMs(toolExecution.getDurationMs());
        entity.setStartedAt(defaultNow(toolExecution.getStartedAt()));
        entity.setCompletedAt(defaultNow(toolExecution.getCompletedAt()));
        entity.setCreatedAt(defaultNow(toolExecution.getCreatedAt()));
        entity.setUpdatedAt(now);
        return entity;
    }

    private boolean shouldSkipToolCallOverride(ToolCallStatus existingStatus, ToolCallStatus targetStatus) {
        return existingStatus == ToolCallStatus.SUCCEEDED && targetStatus != ToolCallStatus.SUCCEEDED;
    }

    private boolean shouldSkipToolExecutionOverride(ToolExecutionStatus existingStatus, ToolExecutionStatus targetStatus) {
        return existingStatus == ToolExecutionStatus.SUCCEEDED && targetStatus != ToolExecutionStatus.SUCCEEDED;
    }

    private void insertMessageIfAbsent(AgentMessageEntity messageEntity) {
        if (messageEntity == null || StringUtils.isBlank(messageEntity.getMessageId())) {
            return;
        }
        AgentMessageEntity existing = messageMapper.selectOne(
            Wrappers.lambdaQuery(AgentMessageEntity.class)
                .eq(AgentMessageEntity::getMessageId, messageEntity.getMessageId())
                .last("limit 1")
        );
        if (existing == null) {
            messageMapper.insert(messageEntity);
        }
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

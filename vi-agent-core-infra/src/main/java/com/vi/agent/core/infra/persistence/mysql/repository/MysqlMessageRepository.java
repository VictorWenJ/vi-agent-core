package com.vi.agent.core.infra.persistence.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageToolCallEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentToolExecutionEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentTurnEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentMessageMapper;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentMessageToolCallMapper;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentToolExecutionMapper;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentTurnMapper;
import com.vi.agent.core.infra.persistence.mysql.message.MessageAggregateRows;
import com.vi.agent.core.infra.persistence.mysql.message.MessageTypeHandlerRegistry;
import com.vi.agent.core.infra.persistence.mysql.message.MessageWritePlan;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.MessageType;
import com.vi.agent.core.model.turn.TurnStatus;
import com.vi.agent.core.model.port.MessageRepository;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MySQL 消息仓储实现。
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
            if (writePlan.getToolExecution() != null) {
                toolExecutionMapper.insert(writePlan.getToolExecution());
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
    public List<Message> findCompletedContextBySessionId(String sessionId, int maxMessages) {
        List<AgentMessageEntity> entities = messageMapper.selectList(
            Wrappers.lambdaQuery(AgentMessageEntity.class)
                .eq(AgentMessageEntity::getSessionId, sessionId)
                .orderByAsc(AgentMessageEntity::getSequenceNo)
        );
        if (CollectionUtils.isEmpty(entities)) {
            return List.of();
        }

        Set<String> completedTurnIds = turnMapper.selectList(
                Wrappers.lambdaQuery(AgentTurnEntity.class)
                    .eq(AgentTurnEntity::getSessionId, sessionId)
                    .eq(AgentTurnEntity::getStatus, TurnStatus.COMPLETED))
            .stream()
            .map(AgentTurnEntity::getTurnId)
            .collect(Collectors.toSet());

        if (CollectionUtils.isEmpty(completedTurnIds)) {
            return List.of();
        }

        List<AgentMessageEntity> completedEntities = entities.stream()
            .filter(entity -> completedTurnIds.contains(entity.getTurnId()))
            .sorted(Comparator.comparingLong(AgentMessageEntity::getSequenceNo))
            .toList();

        if (CollectionUtils.isEmpty(completedEntities)) {
            return List.of();
        }

        List<AgentMessageEntity> limitedEntities = completedEntities;
        if (maxMessages > 0 && completedEntities.size() > maxMessages) {
            limitedEntities = completedEntities.subList(completedEntities.size() - maxMessages, completedEntities.size());
        }

        Map<String, List<AgentMessageToolCallEntity>> toolCallsByAssistantMessageId = loadToolCallsByAssistantMessageId(limitedEntities);
        Map<String, AgentToolExecutionEntity> executionsByToolMessageId = loadToolExecutionsByToolMessageId(limitedEntities);

        List<Message> results = new ArrayList<>(limitedEntities.size());
        for (AgentMessageEntity entity : limitedEntities) {
            MessageAggregateRows aggregateRows = MessageAggregateRows.builder()
                .message(entity)
                .toolCalls(toolCallsByAssistantMessageId.getOrDefault(entity.getMessageId(), List.of()))
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
            return List.of();
        }
        Map<String, List<AgentMessageToolCallEntity>> toolCallsByAssistantMessageId = loadToolCallsByAssistantMessageId(entities);
        Map<String, AgentToolExecutionEntity> executionsByToolMessageId = loadToolExecutionsByToolMessageId(entities);
        return entities.stream()
            .map(entity -> MessageAggregateRows.builder()
                .message(entity)
                .toolCalls(toolCallsByAssistantMessageId.getOrDefault(entity.getMessageId(), List.of()))
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

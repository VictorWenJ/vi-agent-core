package com.vi.agent.core.infra.persistence.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vi.agent.core.infra.persistence.mysql.convertor.MysqlTimeConvertor;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentInternalLlmTaskEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentInternalLlmTaskMapper;
import com.vi.agent.core.model.context.CheckpointTrigger;
import com.vi.agent.core.model.memory.InternalLlmTaskRecord;
import com.vi.agent.core.model.memory.InternalTaskStatus;
import com.vi.agent.core.model.memory.InternalTaskType;
import com.vi.agent.core.model.port.InternalLlmTaskRepository;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 基于 MySQL 的内部 LLM 任务审计仓储实现。
 */
@Repository
public class MysqlInternalLlmTaskRepository implements InternalLlmTaskRepository {

    /** 内部 LLM 任务审计表 Mapper。 */
    @Resource
    private AgentInternalLlmTaskMapper internalLlmTaskMapper;

    /**
     * 保存内部 LLM 任务审计记录。
     *
     * @param task 内部 LLM 任务审计记录
     */
    @Override
    public void save(InternalLlmTaskRecord task) {
        if (task == null) {
            return;
        }
        internalLlmTaskMapper.insert(toEntity(task));
    }

    /**
     * 按内部任务 ID 查询任务记录。
     *
     * @param internalTaskId 内部任务 ID
     * @return 内部 LLM 任务审计记录
     */
    @Override
    public Optional<InternalLlmTaskRecord> findByInternalTaskId(String internalTaskId) {
        if (StringUtils.isBlank(internalTaskId)) {
            return Optional.empty();
        }
        AgentInternalLlmTaskEntity entity = internalLlmTaskMapper.selectOne(
            Wrappers.lambdaQuery(AgentInternalLlmTaskEntity.class)
                .eq(AgentInternalLlmTaskEntity::getInternalTaskId, internalTaskId)
                .last("limit 1")
        );
        return Optional.ofNullable(toModel(entity));
    }

    /**
     * 按 session 查询内部 LLM 任务列表。
     *
     * @param sessionId session ID
     * @return 内部 LLM 任务审计记录列表
     */
    @Override
    public List<InternalLlmTaskRecord> listBySessionId(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return List.of();
        }
        return internalLlmTaskMapper.selectList(
                Wrappers.lambdaQuery(AgentInternalLlmTaskEntity.class)
                    .eq(AgentInternalLlmTaskEntity::getSessionId, sessionId)
                    .orderByAsc(AgentInternalLlmTaskEntity::getId)
            ).stream()
            .map(this::toModel)
            .toList();
    }

    /**
     * 按 run ID 查询内部 LLM 任务列表。
     *
     * @param runId run ID
     * @return 内部 LLM 任务审计记录列表
     */
    @Override
    public List<InternalLlmTaskRecord> listByRunId(String runId) {
        if (StringUtils.isBlank(runId)) {
            return List.of();
        }
        return internalLlmTaskMapper.selectList(
                Wrappers.lambdaQuery(AgentInternalLlmTaskEntity.class)
                    .eq(AgentInternalLlmTaskEntity::getRunId, runId)
                    .orderByAsc(AgentInternalLlmTaskEntity::getId)
            ).stream()
            .map(this::toModel)
            .toList();
    }

    /** 将领域任务记录转换为 MySQL 实体。 */
    private AgentInternalLlmTaskEntity toEntity(InternalLlmTaskRecord task) {
        AgentInternalLlmTaskEntity entity = new AgentInternalLlmTaskEntity();
        entity.setInternalTaskId(task.getInternalTaskId());
        entity.setTaskType(task.getTaskType() == null ? null : task.getTaskType().name());
        entity.setSessionId(task.getSessionId());
        entity.setTurnId(task.getTurnId());
        entity.setRunId(task.getRunId());
        entity.setCheckpointTrigger(task.getCheckpointTrigger() == null ? null : task.getCheckpointTrigger().name());
        entity.setPromptTemplateKey(task.getPromptTemplateKey());
        entity.setPromptTemplateVersion(task.getPromptTemplateVersion());
        entity.setRequestJson(task.getRequestJson());
        entity.setResponseJson(task.getResponseJson());
        entity.setStatus(task.getStatus() == null ? null : task.getStatus().name());
        entity.setErrorCode(task.getErrorCode());
        entity.setErrorMessage(task.getErrorMessage());
        entity.setDurationMs(task.getDurationMs());
        entity.setCreatedAt(MysqlTimeConvertor.toLocalDateTime(effectiveInstant(task.getCreatedAt())));
        entity.setCompletedAt(MysqlTimeConvertor.toLocalDateTime(task.getCompletedAt()));
        return entity;
    }

    /** 将 MySQL 实体还原为领域任务记录。 */
    private InternalLlmTaskRecord toModel(AgentInternalLlmTaskEntity entity) {
        if (entity == null) {
            return null;
        }
        return InternalLlmTaskRecord.builder()
            .internalTaskId(entity.getInternalTaskId())
            .taskType(parseEnum(InternalTaskType.class, entity.getTaskType()))
            .sessionId(entity.getSessionId())
            .turnId(entity.getTurnId())
            .runId(entity.getRunId())
            .checkpointTrigger(parseEnum(CheckpointTrigger.class, entity.getCheckpointTrigger()))
            .promptTemplateKey(entity.getPromptTemplateKey())
            .promptTemplateVersion(entity.getPromptTemplateVersion())
            .requestJson(entity.getRequestJson())
            .responseJson(entity.getResponseJson())
            .status(parseEnum(InternalTaskStatus.class, entity.getStatus()))
            .errorCode(entity.getErrorCode())
            .errorMessage(entity.getErrorMessage())
            .durationMs(entity.getDurationMs())
            .createdAt(MysqlTimeConvertor.toInstant(entity.getCreatedAt()))
            .completedAt(MysqlTimeConvertor.toInstant(entity.getCompletedAt()))
            .build();
    }

    /** 解析枚举名称，空值返回 null。 */
    private <T extends Enum<T>> T parseEnum(Class<T> enumType, String value) {
        return StringUtils.isBlank(value) ? null : Enum.valueOf(enumType, value);
    }

    /** 返回有效时间，空值时使用当前时间。 */
    private Instant effectiveInstant(Instant instant) {
        return instant == null ? Instant.now() : instant;
    }
}

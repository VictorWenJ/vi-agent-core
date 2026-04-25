package com.vi.agent.core.infra.persistence.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vi.agent.core.infra.persistence.mysql.convertor.MysqlTimeConvertor;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentWorkingContextSnapshotEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentWorkingContextSnapshotMapper;
import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.context.CheckpointTrigger;
import com.vi.agent.core.model.context.ContextViewType;
import com.vi.agent.core.model.context.WorkingContextSnapshotRecord;
import com.vi.agent.core.model.port.WorkingContextSnapshotRepository;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 基于 MySQL 的 WorkingContext 审计快照仓储实现。
 */
@Repository
public class MysqlWorkingContextSnapshotRepository implements WorkingContextSnapshotRepository {

    /** WorkingContext 审计快照表 Mapper。 */
    @Resource
    private AgentWorkingContextSnapshotMapper workingContextSnapshotMapper;

    /**
     * 保存 WorkingContext 审计快照。
     *
     * @param snapshot WorkingContext 审计快照
     */
    @Override
    public void save(WorkingContextSnapshotRecord snapshot) {
        if (snapshot == null) {
            return;
        }
        workingContextSnapshotMapper.insert(toEntity(snapshot));
    }

    /**
     * 按快照 ID 查询 WorkingContext 审计快照。
     *
     * @param workingContextSnapshotId WorkingContext 审计快照 ID
     * @return WorkingContext 审计快照
     */
    @Override
    public Optional<WorkingContextSnapshotRecord> findBySnapshotId(String workingContextSnapshotId) {
        if (StringUtils.isBlank(workingContextSnapshotId)) {
            return Optional.empty();
        }
        AgentWorkingContextSnapshotEntity entity = workingContextSnapshotMapper.selectOne(
            Wrappers.lambdaQuery(AgentWorkingContextSnapshotEntity.class)
                .eq(AgentWorkingContextSnapshotEntity::getWorkingContextSnapshotId, workingContextSnapshotId)
                .last("limit 1")
        );
        return Optional.ofNullable(toModel(entity));
    }

    /**
     * 查询 session 下最新 WorkingContext 审计快照。
     *
     * @param sessionId session ID
     * @return 最新 WorkingContext 审计快照
     */
    @Override
    public Optional<WorkingContextSnapshotRecord> findLatestBySessionId(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return Optional.empty();
        }
        AgentWorkingContextSnapshotEntity entity = workingContextSnapshotMapper.selectOne(
            Wrappers.lambdaQuery(AgentWorkingContextSnapshotEntity.class)
                .eq(AgentWorkingContextSnapshotEntity::getSessionId, sessionId)
                .orderByDesc(AgentWorkingContextSnapshotEntity::getId)
                .last("limit 1")
        );
        return Optional.ofNullable(toModel(entity));
    }

    /**
     * 按 run ID 查询 WorkingContext 审计快照列表。
     *
     * @param runId run ID
     * @return WorkingContext 审计快照列表
     */
    @Override
    public List<WorkingContextSnapshotRecord> listByRunId(String runId) {
        if (StringUtils.isBlank(runId)) {
            return List.of();
        }
        return workingContextSnapshotMapper.selectList(
                Wrappers.lambdaQuery(AgentWorkingContextSnapshotEntity.class)
                    .eq(AgentWorkingContextSnapshotEntity::getRunId, runId)
                    .orderByAsc(AgentWorkingContextSnapshotEntity::getContextBuildSeq)
            ).stream()
            .map(this::toModel)
            .toList();
    }

    /** 将领域审计快照转换为 MySQL 实体。 */
    private AgentWorkingContextSnapshotEntity toEntity(WorkingContextSnapshotRecord snapshot) {
        AgentWorkingContextSnapshotEntity entity = new AgentWorkingContextSnapshotEntity();
        entity.setWorkingContextSnapshotId(snapshot.getWorkingContextSnapshotId());
        entity.setConversationId(snapshot.getConversationId());
        entity.setSessionId(snapshot.getSessionId());
        entity.setTurnId(snapshot.getTurnId());
        entity.setRunId(snapshot.getRunId());
        entity.setContextBuildSeq(snapshot.getContextBuildSeq());
        entity.setModelCallSequenceNo(snapshot.getModelCallSequenceNo());
        entity.setCheckpointTrigger(snapshot.getCheckpointTrigger() == null ? null : snapshot.getCheckpointTrigger().name());
        entity.setContextViewType(snapshot.getContextViewType() == null ? null : snapshot.getContextViewType().name());
        entity.setAgentMode(snapshot.getAgentMode() == null ? null : snapshot.getAgentMode().name());
        entity.setTranscriptSnapshotVersion(snapshot.getTranscriptSnapshotVersion());
        entity.setWorkingSetVersion(snapshot.getWorkingSetVersion());
        entity.setStateVersion(snapshot.getStateVersion());
        entity.setSummaryVersion(snapshot.getSummaryVersion());
        entity.setBudgetJson(snapshot.getBudgetJson());
        entity.setBlockSetJson(snapshot.getBlockSetJson());
        entity.setContextJson(snapshot.getContextJson());
        entity.setProjectionJson(snapshot.getProjectionJson());
        entity.setCreatedAt(MysqlTimeConvertor.toLocalDateTime(effectiveInstant(snapshot.getCreatedAt())));
        return entity;
    }

    /** 将 MySQL 实体还原为领域审计快照。 */
    private WorkingContextSnapshotRecord toModel(AgentWorkingContextSnapshotEntity entity) {
        if (entity == null) {
            return null;
        }
        return WorkingContextSnapshotRecord.builder()
            .workingContextSnapshotId(entity.getWorkingContextSnapshotId())
            .conversationId(entity.getConversationId())
            .sessionId(entity.getSessionId())
            .turnId(entity.getTurnId())
            .runId(entity.getRunId())
            .contextBuildSeq(entity.getContextBuildSeq())
            .modelCallSequenceNo(entity.getModelCallSequenceNo())
            .checkpointTrigger(parseEnum(CheckpointTrigger.class, entity.getCheckpointTrigger()))
            .contextViewType(parseEnum(ContextViewType.class, entity.getContextViewType()))
            .agentMode(parseEnum(AgentMode.class, entity.getAgentMode()))
            .transcriptSnapshotVersion(entity.getTranscriptSnapshotVersion())
            .workingSetVersion(entity.getWorkingSetVersion())
            .stateVersion(entity.getStateVersion())
            .summaryVersion(entity.getSummaryVersion())
            .budgetJson(entity.getBudgetJson())
            .blockSetJson(entity.getBlockSetJson())
            .contextJson(entity.getContextJson())
            .projectionJson(entity.getProjectionJson())
            .createdAt(MysqlTimeConvertor.toInstant(entity.getCreatedAt()))
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

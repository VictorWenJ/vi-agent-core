package com.vi.agent.core.infra.persistence.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vi.agent.core.infra.persistence.mysql.convertor.MysqlTimeConvertor;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentMemoryEvidenceEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentMemoryEvidenceMapper;
import com.vi.agent.core.model.memory.EvidenceRef;
import com.vi.agent.core.model.memory.EvidenceSource;
import com.vi.agent.core.model.memory.EvidenceSourceType;
import com.vi.agent.core.model.memory.EvidenceTarget;
import com.vi.agent.core.model.memory.EvidenceTargetType;
import com.vi.agent.core.model.port.MemoryEvidenceRepository;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 基于 MySQL 的 Memory Evidence 事实源仓储实现。
 */
@Repository
public class MysqlMemoryEvidenceRepository implements MemoryEvidenceRepository {

    /** memory evidence 表 Mapper。 */
    @Resource
    private AgentMemoryEvidenceMapper memoryEvidenceMapper;

    /**
     * 保存 evidence。
     *
     * @param evidenceRef evidence 引用
     */
    @Override
    public void save(EvidenceRef evidenceRef) {
        if (evidenceRef == null) {
            return;
        }
        memoryEvidenceMapper.insert(toEntity(evidenceRef));
    }

    /**
     * 批量保存 evidence。
     *
     * @param evidenceRefs evidence 引用列表
     */
    @Override
    public void saveAll(List<EvidenceRef> evidenceRefs) {
        if (CollectionUtils.isEmpty(evidenceRefs)) {
            return;
        }
        evidenceRefs.stream()
            .filter(evidenceRef -> evidenceRef != null)
            .map(this::toEntity)
            .forEach(memoryEvidenceMapper::insert);
    }

    /**
     * 按 evidence ID 查询 evidence。
     *
     * @param evidenceId evidence ID
     * @return evidence 引用
     */
    @Override
    public Optional<EvidenceRef> findByEvidenceId(String evidenceId) {
        if (StringUtils.isBlank(evidenceId)) {
            return Optional.empty();
        }
        AgentMemoryEvidenceEntity entity = memoryEvidenceMapper.selectOne(
            Wrappers.lambdaQuery(AgentMemoryEvidenceEntity.class)
                .eq(AgentMemoryEvidenceEntity::getEvidenceId, evidenceId)
                .last("limit 1")
        );
        return Optional.ofNullable(toModel(entity));
    }

    /**
     * 按 session 查询 evidence 列表。
     *
     * @param sessionId session ID
     * @return evidence 引用列表
     */
    @Override
    public List<EvidenceRef> listBySessionId(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return List.of();
        }
        return memoryEvidenceMapper.selectList(
                Wrappers.lambdaQuery(AgentMemoryEvidenceEntity.class)
                    .eq(AgentMemoryEvidenceEntity::getSessionId, sessionId)
                    .orderByAsc(AgentMemoryEvidenceEntity::getId)
            ).stream()
            .map(this::toModel)
            .toList();
    }

    /**
     * 按目标对象查询 evidence 列表。
     *
     * @param targetType evidence 目标类型
     * @param targetRef evidence 目标对象 ID
     * @return evidence 引用列表
     */
    @Override
    public List<EvidenceRef> listByTarget(EvidenceTargetType targetType, String targetRef) {
        if (targetType == null || StringUtils.isBlank(targetRef)) {
            return List.of();
        }
        return memoryEvidenceMapper.selectList(
                Wrappers.lambdaQuery(AgentMemoryEvidenceEntity.class)
                    .eq(AgentMemoryEvidenceEntity::getTargetType, targetType.name())
                    .eq(AgentMemoryEvidenceEntity::getTargetRef, targetRef)
                    .orderByAsc(AgentMemoryEvidenceEntity::getId)
            ).stream()
            .map(this::toModel)
            .toList();
    }

    /** 将领域 evidence 转换为 MySQL 实体。 */
    private AgentMemoryEvidenceEntity toEntity(EvidenceRef evidenceRef) {
        EvidenceTarget target = evidenceRef.getTarget();
        EvidenceSource source = evidenceRef.getSource();
        AgentMemoryEvidenceEntity entity = new AgentMemoryEvidenceEntity();
        entity.setEvidenceId(evidenceRef.getEvidenceId());
        entity.setSessionId(source == null ? null : source.getSessionId());
        entity.setTurnId(source == null ? null : source.getTurnId());
        entity.setRunId(source == null ? null : source.getRunId());
        entity.setTargetType(target == null || target.getTargetType() == null ? null : target.getTargetType().name());
        entity.setTargetRef(target == null ? null : target.getTargetRef());
        entity.setTargetField(target == null ? null : target.getTargetField());
        entity.setTargetItemId(target == null ? null : target.getTargetItemId());
        entity.setDisplayPath(target == null ? null : target.getDisplayPath());
        entity.setSourceType(source == null || source.getSourceType() == null ? null : source.getSourceType().name());
        entity.setMessageId(source == null ? null : source.getMessageId());
        entity.setToolCallRecordId(source == null ? null : source.getToolCallRecordId());
        entity.setWorkingContextSnapshotId(source == null ? null : source.getWorkingContextSnapshotId());
        entity.setInternalTaskId(source == null ? null : source.getInternalTaskId());
        entity.setExcerptText(source == null ? null : source.getExcerptText());
        entity.setConfidence(evidenceRef.getConfidence() == null ? null : BigDecimal.valueOf(evidenceRef.getConfidence()));
        entity.setCreatedAt(MysqlTimeConvertor.toLocalDateTime(effectiveInstant(evidenceRef.getCreatedAt())));
        return entity;
    }

    /** 将 MySQL 实体还原为领域 evidence。 */
    private EvidenceRef toModel(AgentMemoryEvidenceEntity entity) {
        if (entity == null) {
            return null;
        }
        return EvidenceRef.builder()
            .evidenceId(entity.getEvidenceId())
            .target(EvidenceTarget.builder()
                .targetType(parseEnum(EvidenceTargetType.class, entity.getTargetType()))
                .targetRef(entity.getTargetRef())
                .targetField(entity.getTargetField())
                .targetItemId(entity.getTargetItemId())
                .displayPath(entity.getDisplayPath())
                .build())
            .source(EvidenceSource.builder()
                .sourceType(parseEnum(EvidenceSourceType.class, entity.getSourceType()))
                .sessionId(entity.getSessionId())
                .turnId(entity.getTurnId())
                .runId(entity.getRunId())
                .messageId(entity.getMessageId())
                .toolCallRecordId(entity.getToolCallRecordId())
                .workingContextSnapshotId(entity.getWorkingContextSnapshotId())
                .internalTaskId(entity.getInternalTaskId())
                .excerptText(entity.getExcerptText())
                .build())
            .confidence(entity.getConfidence() == null ? null : entity.getConfidence().doubleValue())
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

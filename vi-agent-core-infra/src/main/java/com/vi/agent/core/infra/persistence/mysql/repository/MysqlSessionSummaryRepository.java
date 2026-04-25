package com.vi.agent.core.infra.persistence.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vi.agent.core.infra.persistence.mysql.convertor.MysqlTimeConvertor;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentSessionSummaryEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentSessionSummaryMapper;
import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.port.SessionSummaryRepository;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * 基于 MySQL 的 Session Summary 事实源仓储实现。
 */
@Repository
public class MysqlSessionSummaryRepository implements SessionSummaryRepository {

    /** session summary 表 Mapper。 */
    @Resource
    private AgentSessionSummaryMapper sessionSummaryMapper;

    /**
     * 保存 session summary。
     *
     * @param summary session summary
     */
    @Override
    public void save(ConversationSummary summary) {
        if (summary == null) {
            return;
        }
        sessionSummaryMapper.insert(toEntity(summary));
    }

    /**
     * 按 summary ID 查询 summary。
     *
     * @param summaryId summary ID
     * @return session summary
     */
    @Override
    public Optional<ConversationSummary> findBySummaryId(String summaryId) {
        if (StringUtils.isBlank(summaryId)) {
            return Optional.empty();
        }
        AgentSessionSummaryEntity entity = sessionSummaryMapper.selectOne(
            Wrappers.lambdaQuery(AgentSessionSummaryEntity.class)
                .eq(AgentSessionSummaryEntity::getSummaryId, summaryId)
                .last("limit 1")
        );
        return Optional.ofNullable(toModel(entity));
    }

    /**
     * 查询 session 下最新 summary。
     *
     * @param sessionId session ID
     * @return 最新 session summary
     */
    @Override
    public Optional<ConversationSummary> findLatestBySessionId(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return Optional.empty();
        }
        AgentSessionSummaryEntity entity = sessionSummaryMapper.selectOne(
            Wrappers.lambdaQuery(AgentSessionSummaryEntity.class)
                .eq(AgentSessionSummaryEntity::getSessionId, sessionId)
                .orderByDesc(AgentSessionSummaryEntity::getSummaryVersion)
                .last("limit 1")
        );
        return Optional.ofNullable(toModel(entity));
    }

    /**
     * 按 session 与版本查询 summary。
     *
     * @param sessionId session ID
     * @param summaryVersion summary 版本号
     * @return session summary
     */
    @Override
    public Optional<ConversationSummary> findBySessionIdAndSummaryVersion(String sessionId, Long summaryVersion) {
        if (StringUtils.isBlank(sessionId) || summaryVersion == null) {
            return Optional.empty();
        }
        AgentSessionSummaryEntity entity = sessionSummaryMapper.selectOne(
            Wrappers.lambdaQuery(AgentSessionSummaryEntity.class)
                .eq(AgentSessionSummaryEntity::getSessionId, sessionId)
                .eq(AgentSessionSummaryEntity::getSummaryVersion, summaryVersion)
                .last("limit 1")
        );
        return Optional.ofNullable(toModel(entity));
    }

    /** 将领域 summary 转换为 MySQL 实体。 */
    private AgentSessionSummaryEntity toEntity(ConversationSummary summary) {
        Instant effectiveCreatedAt = summary.getCreatedAt() == null ? Instant.now() : summary.getCreatedAt();
        AgentSessionSummaryEntity entity = new AgentSessionSummaryEntity();
        entity.setSummaryId(summary.getSummaryId());
        entity.setSessionId(summary.getSessionId());
        entity.setSummaryVersion(summary.getSummaryVersion());
        entity.setCoveredFromSequenceNo(summary.getCoveredFromSequenceNo());
        entity.setCoveredToSequenceNo(summary.getCoveredToSequenceNo());
        entity.setSummaryText(summary.getSummaryText());
        entity.setSummaryTemplateKey(summary.getSummaryTemplateKey());
        entity.setSummaryTemplateVersion(summary.getSummaryTemplateVersion());
        entity.setGeneratorProvider(summary.getGeneratorProvider());
        entity.setGeneratorModel(summary.getGeneratorModel());
        entity.setSourceRunId(null);
        entity.setCreatedAt(MysqlTimeConvertor.toLocalDateTime(effectiveCreatedAt));
        return entity;
    }

    /** 将 MySQL 实体还原为领域 summary。 */
    private ConversationSummary toModel(AgentSessionSummaryEntity entity) {
        if (entity == null) {
            return null;
        }
        return ConversationSummary.builder()
            .summaryId(entity.getSummaryId())
            .sessionId(entity.getSessionId())
            .summaryVersion(entity.getSummaryVersion())
            .coveredFromSequenceNo(entity.getCoveredFromSequenceNo())
            .coveredToSequenceNo(entity.getCoveredToSequenceNo())
            .summaryText(entity.getSummaryText())
            .summaryTemplateKey(entity.getSummaryTemplateKey())
            .summaryTemplateVersion(entity.getSummaryTemplateVersion())
            .generatorProvider(entity.getGeneratorProvider())
            .generatorModel(entity.getGeneratorModel())
            .createdAt(MysqlTimeConvertor.toInstant(entity.getCreatedAt()))
            .build();
    }
}

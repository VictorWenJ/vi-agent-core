package com.vi.agent.core.infra.persistence.mysql.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.infra.persistence.mysql.convertor.MysqlTimeConvertor;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentSessionStateEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentSessionStateMapper;
import com.vi.agent.core.model.memory.SessionStateSnapshot;
import com.vi.agent.core.model.port.SessionStateRepository;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * 基于 MySQL 的 Session State 事实源仓储实现。
 */
@Repository
public class MysqlSessionStateRepository implements SessionStateRepository {

    /** session state 快照表 Mapper。 */
    @Resource
    private AgentSessionStateMapper sessionStateMapper;

    /**
     * 保存 session state 快照。
     *
     * @param snapshot session state 快照
     */
    @Override
    public void save(SessionStateSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        sessionStateMapper.insert(toEntity(snapshot));
    }

    /**
     * 按快照 ID 查询 session state。
     *
     * @param snapshotId state 快照 ID
     * @return session state 快照
     */
    @Override
    public Optional<SessionStateSnapshot> findBySnapshotId(String snapshotId) {
        if (StringUtils.isBlank(snapshotId)) {
            return Optional.empty();
        }
        AgentSessionStateEntity entity = sessionStateMapper.selectOne(
            Wrappers.lambdaQuery(AgentSessionStateEntity.class)
                .eq(AgentSessionStateEntity::getSnapshotId, snapshotId)
                .last("limit 1")
        );
        return Optional.ofNullable(toModel(entity));
    }

    /**
     * 查询 session 下最新 session state。
     *
     * @param sessionId session ID
     * @return 最新 session state 快照
     */
    @Override
    public Optional<SessionStateSnapshot> findLatestBySessionId(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return Optional.empty();
        }
        AgentSessionStateEntity entity = sessionStateMapper.selectOne(
            Wrappers.lambdaQuery(AgentSessionStateEntity.class)
                .eq(AgentSessionStateEntity::getSessionId, sessionId)
                .orderByDesc(AgentSessionStateEntity::getStateVersion)
                .last("limit 1")
        );
        return Optional.ofNullable(toModel(entity));
    }

    /**
     * 按 session 与版本查询 session state。
     *
     * @param sessionId session ID
     * @param stateVersion state 版本号
     * @return session state 快照
     */
    @Override
    public Optional<SessionStateSnapshot> findBySessionIdAndStateVersion(String sessionId, Long stateVersion) {
        if (StringUtils.isBlank(sessionId) || stateVersion == null) {
            return Optional.empty();
        }
        AgentSessionStateEntity entity = sessionStateMapper.selectOne(
            Wrappers.lambdaQuery(AgentSessionStateEntity.class)
                .eq(AgentSessionStateEntity::getSessionId, sessionId)
                .eq(AgentSessionStateEntity::getStateVersion, stateVersion)
                .last("limit 1")
        );
        return Optional.ofNullable(toModel(entity));
    }

    /** 将领域快照转换为 MySQL 实体。 */
    private AgentSessionStateEntity toEntity(SessionStateSnapshot snapshot) {
        Instant effectiveUpdatedAt = snapshot.getUpdatedAt() == null ? Instant.now() : snapshot.getUpdatedAt();
        AgentSessionStateEntity entity = new AgentSessionStateEntity();
        entity.setSnapshotId(snapshot.getSnapshotId());
        entity.setSessionId(snapshot.getSessionId());
        entity.setStateVersion(snapshot.getStateVersion());
        entity.setTaskGoal(snapshot.getTaskGoal());
        entity.setStateJson(JsonUtils.toJson(snapshot));
        entity.setSourceRunId(null);
        entity.setCreatedAt(MysqlTimeConvertor.toLocalDateTime(effectiveUpdatedAt));
        entity.setUpdatedAt(MysqlTimeConvertor.toLocalDateTime(effectiveUpdatedAt));
        return entity;
    }

    /** 将 MySQL 实体还原为领域快照。 */
    private SessionStateSnapshot toModel(AgentSessionStateEntity entity) {
        if (entity == null || StringUtils.isBlank(entity.getStateJson())) {
            return null;
        }
        return JsonUtils.jsonToBean(entity.getStateJson(), SessionStateSnapshot.class);
    }
}

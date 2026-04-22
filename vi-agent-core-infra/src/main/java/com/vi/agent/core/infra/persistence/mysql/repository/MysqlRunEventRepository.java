package com.vi.agent.core.infra.persistence.mysql.repository;

import com.vi.agent.core.infra.persistence.mysql.convertor.MysqlTimeConvertor;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentRunEventEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentRunEventMapper;
import com.vi.agent.core.model.port.RunEventRepository;
import com.vi.agent.core.model.runtime.RunEventRecord;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Run 事件 MySQL 仓储实现。
 */
@Repository
public class MysqlRunEventRepository implements RunEventRepository {

    @Resource
    private AgentRunEventMapper runEventMapper;

    @Override
    public void saveBatch(List<RunEventRecord> runEvents) {
        if (CollectionUtils.isEmpty(runEvents)) {
            return;
        }
        for (RunEventRecord runEvent : runEvents) {
            if (runEvent == null) {
                continue;
            }
            runEventMapper.insert(toEntity(runEvent));
        }
    }

    private AgentRunEventEntity toEntity(RunEventRecord runEvent) {
        AgentRunEventEntity entity = new AgentRunEventEntity();
        entity.setEventId(runEvent.getEventId());
        entity.setConversationId(runEvent.getConversationId());
        entity.setSessionId(runEvent.getSessionId());
        entity.setTurnId(runEvent.getTurnId());
        entity.setRunId(runEvent.getRunId());
        entity.setEventIndex(runEvent.getEventIndex());
        entity.setEventType(runEvent.getEventType());
        entity.setActorType(runEvent.getActorType());
        entity.setActorId(runEvent.getActorId());
        entity.setPayloadJson(runEvent.getPayloadJson());
        entity.setCreatedAt(MysqlTimeConvertor.toLocalDateTime(defaultNow(runEvent.getCreatedAt())));
        return entity;
    }

    private Instant defaultNow(Instant instant) {
        return instant == null ? Instant.now() : instant;
    }
}

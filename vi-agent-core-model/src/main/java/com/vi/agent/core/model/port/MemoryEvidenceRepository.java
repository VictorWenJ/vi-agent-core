package com.vi.agent.core.model.port;

import com.vi.agent.core.model.memory.EvidenceRef;
import com.vi.agent.core.model.memory.EvidenceTargetType;

import java.util.List;
import java.util.Optional;

/**
 * Memory Evidence 事实源仓储端口。
 */
public interface MemoryEvidenceRepository {

    /** 保存 evidence。 */
    void save(EvidenceRef evidenceRef);

    /** 批量保存 evidence。 */
    void saveAll(List<EvidenceRef> evidenceRefs);

    /** 按 evidence ID 查询 evidence。 */
    Optional<EvidenceRef> findByEvidenceId(String evidenceId);

    /** 按 session 查询 evidence 列表。 */
    List<EvidenceRef> listBySessionId(String sessionId);

    /** 按目标对象查询 evidence 列表。 */
    List<EvidenceRef> listByTarget(EvidenceTargetType targetType, String targetRef);
}

package com.vi.agent.core.model.port;

import com.vi.agent.core.model.memory.InternalLlmTaskRecord;

import java.util.List;
import java.util.Optional;

/**
 * 内部 LLM 任务审计仓储端口。
 */
public interface InternalLlmTaskRepository {

    /** 保存内部 LLM 任务审计记录。 */
    void save(InternalLlmTaskRecord task);

    /** 按内部任务 ID 查询任务记录。 */
    Optional<InternalLlmTaskRecord> findByInternalTaskId(String internalTaskId);

    /** 按 session 查询任务记录列表。 */
    List<InternalLlmTaskRecord> listBySessionId(String sessionId);

    /** 按 run ID 查询任务记录列表。 */
    List<InternalLlmTaskRecord> listByRunId(String runId);
}

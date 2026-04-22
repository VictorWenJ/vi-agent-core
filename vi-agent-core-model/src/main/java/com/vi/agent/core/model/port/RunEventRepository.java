package com.vi.agent.core.model.port;

import com.vi.agent.core.model.runtime.RunEventRecord;

import java.util.List;

/**
 * Run 事件事实仓储端口。
 */
public interface RunEventRepository {

    void saveBatch(List<RunEventRecord> runEvents);
}

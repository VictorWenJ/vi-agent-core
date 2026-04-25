package com.vi.agent.core.app.config;

import com.vi.agent.core.common.id.ContextBlockIdGenerator;
import com.vi.agent.core.common.id.InternalTaskIdGenerator;
import com.vi.agent.core.common.id.InternalTaskMessageIdGenerator;
import com.vi.agent.core.common.id.RunEventIdGenerator;
import com.vi.agent.core.common.id.SessionStateSnapshotIdGenerator;
import com.vi.agent.core.common.id.ToolCallRecordIdGenerator;
import com.vi.agent.core.common.id.ToolExecutionIdGenerator;
import com.vi.agent.core.common.id.WorkingContextProjectionIdGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class IdGeneratorConfigTest {

    @Test
    void configShouldExposeAllRuntimeIdGenerators() {
        IdGeneratorConfig config = new IdGeneratorConfig();

        assertInstanceOf(ToolCallRecordIdGenerator.class, config.toolCallRecordIdGenerator());
        assertInstanceOf(ToolExecutionIdGenerator.class, config.toolExecutionIdGenerator());
        assertInstanceOf(RunEventIdGenerator.class, config.runEventIdGenerator());
        assertInstanceOf(ContextBlockIdGenerator.class, config.contextBlockIdGenerator());
        assertInstanceOf(WorkingContextProjectionIdGenerator.class, config.workingContextProjectionIdGenerator());
        assertInstanceOf(SessionStateSnapshotIdGenerator.class, config.sessionStateSnapshotIdGenerator());
        assertInstanceOf(InternalTaskIdGenerator.class, config.internalTaskIdGenerator());
        assertInstanceOf(InternalTaskMessageIdGenerator.class, config.internalTaskMessageIdGenerator());
    }
}

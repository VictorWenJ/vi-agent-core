package com.vi.agent.core.common.id;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdGeneratorContractTest {

    @Test
    void missingRuntimeIdGeneratorsShouldUseStablePrefixesAndUniqueValues() {
        assertGenerator(new ToolCallRecordIdGenerator(), "tcr-");
        assertGenerator(new ToolExecutionIdGenerator(), "tex-");
        assertGenerator(new RunEventIdGenerator(), "evt-");
        assertGenerator(new ContextBlockIdGenerator(), "ctxblk-");
        assertGenerator(new WorkingContextProjectionIdGenerator(), "wcp-");
        assertGenerator(new SessionStateSnapshotIdGenerator(), "state-");
        assertGenerator(new InternalTaskIdGenerator(), "itask-");
    }

    @Test
    void internalTaskMessageIdShouldIncludeRoleSegment() {
        InternalTaskMessageIdGenerator generator = new InternalTaskMessageIdGenerator();

        String systemId = generator.nextId("system");
        String userId = generator.nextId("user");

        assertTrue(systemId.startsWith("itaskmsg-system-"));
        assertTrue(userId.startsWith("itaskmsg-user-"));
        assertNotEquals(systemId, userId);
    }

    private void assertGenerator(IdGenerator generator, String expectedPrefix) {
        String firstId = generator.nextId();
        String secondId = generator.nextId();

        assertTrue(firstId.startsWith(expectedPrefix));
        assertTrue(secondId.startsWith(expectedPrefix));
        assertNotEquals(firstId, secondId);
    }
}

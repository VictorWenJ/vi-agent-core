package com.vi.agent.core.runtime.context.projector;

import com.vi.agent.core.model.context.ContextBlockType;
import com.vi.agent.core.model.message.MessageRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyntheticMessageIdGeneratorTest {

    @Test
    void newSyntheticMessageIdShouldUseStablePrefixAndUniqueSuffix() {
        SyntheticMessageIdGenerator generator = new SyntheticMessageIdGenerator();

        String runtimeId = generator.newSyntheticMessageId(MessageRole.SYSTEM, ContextBlockType.RUNTIME_INSTRUCTION);
        String stateId = generator.newSyntheticMessageId(MessageRole.SUMMARY, ContextBlockType.SESSION_STATE);
        String anotherStateId = generator.newSyntheticMessageId(MessageRole.SUMMARY, ContextBlockType.SESSION_STATE);

        assertTrue(runtimeId.startsWith("ctxmsg-system-runtime_instruction-"));
        assertTrue(stateId.startsWith("ctxmsg-summary-session_state-"));
        assertNotEquals(stateId, anotherStateId);
    }
}

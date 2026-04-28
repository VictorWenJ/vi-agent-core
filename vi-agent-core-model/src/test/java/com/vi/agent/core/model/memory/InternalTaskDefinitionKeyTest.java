package com.vi.agent.core.model.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalTaskDefinitionKeyTest {

    @Test
    void evidenceBindDeterministicShouldStayInMemoryPackageWithStableValue() {
        assertEquals("com.vi.agent.core.model.memory", InternalTaskDefinitionKey.class.getPackageName());
        assertEquals("evidence_bind_deterministic", InternalTaskDefinitionKey.EVIDENCE_BIND_DETERMINISTIC.getValue());
        assertTrue(InternalTaskDefinitionKey.EVIDENCE_BIND_DETERMINISTIC.getDescription().contains("证据"));
    }
}

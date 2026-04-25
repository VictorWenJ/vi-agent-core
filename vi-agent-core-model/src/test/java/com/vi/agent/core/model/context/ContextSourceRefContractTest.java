package com.vi.agent.core.model.context;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.context.block.ContextSourceRef;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextSourceRefContractTest {

    @Test
    void sourceTypeShouldUseContextSourceTypeEnum() throws Exception {
        Field sourceTypeField = ContextSourceRef.class.getDeclaredField("sourceType");

        assertEquals(ContextSourceType.class, sourceTypeField.getType());
    }

    @Test
    void sourceVersionShouldExistAsStringField() throws Exception {
        Field sourceVersionField = ContextSourceRef.class.getDeclaredField("sourceVersion");

        assertEquals(String.class, sourceVersionField.getType());
    }

    @Test
    void builderShouldSetSourceVersionAndJsonShouldRoundTripContractFields() {
        ContextSourceRef sourceRef = ContextSourceRef.builder()
            .sourceType(ContextSourceType.SESSION_STATE_SNAPSHOT)
            .sourceId("state-1")
            .sourceVersion("3")
            .fieldPath("state")
            .build();

        String json = JsonUtils.toJson(sourceRef);
        ContextSourceRef restored = JsonUtils.jsonToBean(json, ContextSourceRef.class);

        assertEquals("3", sourceRef.getSourceVersion());
        assertTrue(json.contains("\"sourceType\":\"SESSION_STATE_SNAPSHOT\""));
        assertTrue(json.contains("\"sourceId\":\"state-1\""));
        assertTrue(json.contains("\"sourceVersion\":\"3\""));
        assertTrue(json.contains("\"fieldPath\":\"state\""));
        assertEquals(ContextSourceType.SESSION_STATE_SNAPSHOT, restored.getSourceType());
        assertEquals("state-1", restored.getSourceId());
        assertEquals("3", restored.getSourceVersion());
        assertEquals("state", restored.getFieldPath());
    }
}

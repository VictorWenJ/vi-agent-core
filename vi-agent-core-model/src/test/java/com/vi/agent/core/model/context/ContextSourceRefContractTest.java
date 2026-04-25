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
    void sourceTypeShouldSerializeAsReadableEnumName() {
        ContextSourceRef sourceRef = ContextSourceRef.builder()
            .sourceType(ContextSourceType.SESSION_STATE_SNAPSHOT)
            .sourceId("state-1")
            .fieldPath("state")
            .build();

        String json = JsonUtils.toJson(sourceRef);

        assertTrue(json.contains("\"sourceType\":\"SESSION_STATE_SNAPSHOT\""));
    }
}

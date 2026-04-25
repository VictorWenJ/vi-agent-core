package com.vi.agent.core.model.context;

import com.vi.agent.core.common.util.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextReferenceTest {

    @Test
    void shouldKeepLightweightReferenceFieldsInContextPackage() {
        ContextReference reference = ContextReference.builder()
            .referenceId("ref-1")
            .referenceType("ARTIFACT")
            .displayText("可按需加载的资料")
            .targetRef("artifact-1")
            .loadHint("load when user asks details")
            .build();

        assertEquals("ref-1", reference.getReferenceId());
        assertEquals("ARTIFACT", reference.getReferenceType());
        assertEquals("artifact-1", reference.getTargetRef());
    }

    @Test
    void shouldSerializeAndDeserializeContextReference() {
        ContextReference reference = ContextReference.builder()
            .referenceId("ref-1")
            .referenceType("ARTIFACT")
            .displayText("可按需加载的资料")
            .targetRef("artifact-1")
            .loadHint("load when user asks details")
            .build();

        ContextReference restored = JsonUtils.jsonToBean(JsonUtils.toJson(reference), ContextReference.class);

        assertEquals("ref-1", restored.getReferenceId());
        assertEquals("可按需加载的资料", restored.getDisplayText());
        assertEquals("load when user asks details", restored.getLoadHint());
    }
}
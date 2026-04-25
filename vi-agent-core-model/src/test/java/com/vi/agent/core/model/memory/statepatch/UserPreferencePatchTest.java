package com.vi.agent.core.model.memory.statepatch;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.memory.AnswerStyle;
import com.vi.agent.core.model.memory.DetailLevel;
import com.vi.agent.core.model.memory.TermFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPreferencePatchTest {

    @Test
    void isEmptyShouldReturnTrueWhenNoFieldIsExplicitlyUpdated() {
        UserPreferencePatch patch = UserPreferencePatch.builder().build();

        assertTrue(patch.isEmpty());
    }

    @Test
    void isEmptyShouldReturnFalseWhenAnyFieldIsExplicitlyUpdated() {
        UserPreferencePatch patch = UserPreferencePatch.builder()
            .answerStyle(AnswerStyle.STRUCTURED)
            .build();

        assertFalse(patch.isEmpty());
    }

    @Test
    void shouldKeepTypedPatchFieldsAfterJsonRoundTrip() {
        UserPreferencePatch patch = UserPreferencePatch.builder()
            .answerStyle(AnswerStyle.DIRECT)
            .detailLevel(DetailLevel.DETAILED)
            .termFormat(TermFormat.BILINGUAL)
            .build();

        UserPreferencePatch restored = JsonUtils.jsonToBean(JsonUtils.toJson(patch), UserPreferencePatch.class);

        assertEquals(AnswerStyle.DIRECT, restored.getAnswerStyle());
        assertEquals(DetailLevel.DETAILED, restored.getDetailLevel());
        assertEquals(TermFormat.BILINGUAL, restored.getTermFormat());
    }
}

package com.vi.agent.core.runtime.memory.extract;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateDeltaExtractionOutputParserTest {

    private final StateDeltaExtractionOutputParser parser = new StateDeltaExtractionOutputParser();

    @Test
    void validJsonShouldParseStateDelta() {
        StateDeltaExtractionResult result = parser.parse("""
            {
              "confirmedFactsAppend": [
                {
                  "factId": "fact-1",
                  "category": "preference",
                  "content": "User prefers concise checklists."
                }
              ],
              "sourceCandidateIds": ["msg-user-1"]
            }
            """);

        assertTrue(result.isSuccess());
        assertFalse(result.isDegraded());
        assertNotNull(result.getStateDelta());
        assertFalse(result.getStateDelta().isEmpty());
        assertEquals("User prefers concise checklists.", result.getStateDelta().getConfirmedFactsAppend().get(0).getContent());
        assertEquals(List.of("msg-user-1"), result.getSourceCandidateIds());
    }

    @Test
    void emptyDeltaShouldParseAsSuccessfulEmptyDelta() {
        StateDeltaExtractionResult result = parser.parse("""
            {
              "sourceCandidateIds": []
            }
            """);

        assertTrue(result.isSuccess());
        assertFalse(result.isDegraded());
        assertNotNull(result.getStateDelta());
        assertTrue(result.getStateDelta().isEmpty());
        assertTrue(result.getSourceCandidateIds().isEmpty());
    }

    @Test
    void invalidJsonShouldReturnDegradedResult() {
        StateDeltaExtractionResult result = parser.parse("{not-json");

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("Invalid StateDelta JSON"));
    }

    @Test
    void oldUpsertRemoveSemanticsShouldBeRejected() {
        StateDeltaExtractionResult result = parser.parse("""
            {
              "upsert": [],
              "remove": [],
              "sourceCandidateIds": []
            }
            """);

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("upsert"));
        assertTrue(result.getFailureReason().contains("remove"));
    }

    @Test
    void unknownFieldShouldBeRejected() {
        StateDeltaExtractionResult result = parser.parse("""
            {
              "messages": [],
              "sourceCandidateIds": []
            }
            """);

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("messages"));
    }
}

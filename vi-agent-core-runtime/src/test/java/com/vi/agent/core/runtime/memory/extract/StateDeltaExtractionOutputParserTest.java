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
                  "content": "User prefers concise checklists.",
                  "confidence": 0.9,
                  "lastVerifiedAt": "2026-04-26T00:00:00Z",
                  "stalePolicy": "SESSION"
                }
              ],
              "constraintsAppend": [
                {
                  "constraintId": "constraint-1",
                  "content": "Do not expose internal evidence ids.",
                  "scope": "SESSION",
                  "confidence": 0.95,
                  "lastVerifiedAt": "2026-04-26T00:00:00Z"
                }
              ],
              "decisionsAppend": [
                {
                  "decisionId": "decision-1",
                  "content": "Use concise checklist.",
                  "decidedBy": "USER",
                  "confidence": 0.9
                }
              ],
              "openLoopsAppend": [
                {
                  "loopId": "loop-1",
                  "kind": "FOLLOW_UP_ACTION",
                  "content": "Collect I-20.",
                  "status": "OPEN",
                  "sourceType": "USER",
                  "sourceRef": "msg-user-1",
                  "createdAt": "2026-04-26T00:00:00Z"
                }
              ],
              "recentToolOutcomesAppend": [
                {
                  "digestId": "digest-1",
                  "toolCallRecordId": "tcr-1",
                  "toolExecutionId": "tex-1",
                  "toolName": "searchSchools",
                  "summary": "Found three candidate schools.",
                  "freshnessPolicy": "SESSION",
                  "validUntil": "2026-04-27T00:00:00Z",
                  "lastVerifiedAt": "2026-04-26T00:00:00Z"
                }
              ],
              "userPreferencesPatch": {
                "answerStyle": "DIRECT",
                "detailLevel": "HIGH",
                "termFormat": "ENGLISH_ZH"
              },
              "phaseStatePatch": {
                "promptEngineeringEnabled": true,
                "contextAuditEnabled": true,
                "summaryEnabled": false,
                "stateExtractionEnabled": true,
                "compactionEnabled": false
              },
              "sourceCandidateIds": ["msg-user-1"]
            }
            """);

        assertTrue(result.isSuccess());
        assertFalse(result.isDegraded());
        assertNotNull(result.getStateDelta());
        assertFalse(result.getStateDelta().isEmpty());
        assertEquals("User prefers concise checklists.", result.getStateDelta().getConfirmedFactsAppend().get(0).getContent());
        assertEquals("Use concise checklist.", result.getStateDelta().getDecisionsAppend().get(0).getContent());
        assertEquals("Collect I-20.", result.getStateDelta().getOpenLoopsAppend().get(0).getContent());
        assertEquals("Found three candidate schools.", result.getStateDelta().getRecentToolOutcomesAppend().get(0).getSummary());
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

    @Test
    void nestedConfirmedFactUnknownFieldShouldBeRejected() {
        StateDeltaExtractionResult result = parser.parse("""
            {
              "confirmedFactsAppend": [
                {
                  "factId": "fact-1",
                  "content": "User prefers concise checklists.",
                  "category": "old category"
                }
              ],
              "sourceCandidateIds": []
            }
            """);

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("confirmedFactsAppend[0].category"));
    }

    @Test
    void nestedConstraintUnknownFieldShouldBeRejected() {
        StateDeltaExtractionResult result = parser.parse("""
            {
              "constraintsAppend": [
                {
                  "constraintId": "constraint-1",
                  "content": "Do not expose internal evidence ids.",
                  "active": true
                }
              ],
              "sourceCandidateIds": []
            }
            """);

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("constraintsAppend[0].active"));
    }

    @Test
    void oldDecisionRecordFieldsShouldBeRejected() {
        StateDeltaExtractionResult result = parser.parse("""
            {
              "decisionsAppend": [
                {
                  "decisionId": "decision-1",
                  "title": "old title",
                  "decisionText": "old text"
                }
              ],
              "sourceCandidateIds": []
            }
            """);

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("decisionsAppend[0].title"));
        assertTrue(result.getFailureReason().contains("decisionsAppend[0].decisionText"));
    }

    @Test
    void oldOpenLoopFieldsShouldBeRejected() {
        StateDeltaExtractionResult result = parser.parse("""
            {
              "openLoopsAppend": [
                {
                  "openLoopId": "loop-1",
                  "title": "old title",
                  "description": "old description"
                }
              ],
              "sourceCandidateIds": []
            }
            """);

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("openLoopsAppend[0].openLoopId"));
        assertTrue(result.getFailureReason().contains("openLoopsAppend[0].title"));
        assertTrue(result.getFailureReason().contains("openLoopsAppend[0].description"));
    }

    @Test
    void oldToolOutcomeFieldsShouldBeRejected() {
        StateDeltaExtractionResult result = parser.parse("""
            {
              "recentToolOutcomesAppend": [
                {
                  "digestId": "digest-1",
                  "digestText": "old digest",
                  "expiresAt": "2026-04-27T00:00:00Z"
                }
              ],
              "sourceCandidateIds": []
            }
            """);

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("recentToolOutcomesAppend[0].digestText"));
        assertTrue(result.getFailureReason().contains("recentToolOutcomesAppend[0].expiresAt"));
    }

    @Test
    void userPreferencePatchUnknownFieldShouldBeRejected() {
        StateDeltaExtractionResult result = parser.parse("""
            {
              "userPreferencesPatch": {
                "answerStyle": "DIRECT",
                "unknownPreference": "value"
              },
              "sourceCandidateIds": []
            }
            """);

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("userPreferencesPatch.unknownPreference"));
    }

    @Test
    void phaseStatePatchUnknownFieldShouldBeRejected() {
        StateDeltaExtractionResult result = parser.parse("""
            {
              "phaseStatePatch": {
                "summaryEnabled": true,
                "unknownPhaseFlag": false
              },
              "sourceCandidateIds": []
            }
            """);

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("phaseStatePatch.unknownPhaseFlag"));
    }
}

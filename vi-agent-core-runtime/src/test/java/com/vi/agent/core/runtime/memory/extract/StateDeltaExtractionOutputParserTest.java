package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.runtime.prompt.PromptContractTestSupport;
import com.vi.agent.core.runtime.prompt.StructuredLlmOutputContractGuard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateDeltaExtractionOutputParserTest {

    private final StateDeltaExtractionOutputParser parser = new StateDeltaExtractionOutputParser(
        PromptContractTestSupport.stateDeltaContract(),
        new StructuredLlmOutputContractGuard()
    );

    @Test
    void validJsonShouldParseStateDeltaAfterSchemaGuardPasses() {
        StateDeltaExtractionResult result = parser.parse(
            PromptContractTestSupport.fixture("prompt-fixtures/state-delta/valid-output.json")
        );

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
    void sourceCandidateIdsOnlyShouldNotCountAsDurableStateChange() {
        StateDeltaExtractionResult result = parser.parse("""
            {
              "sourceCandidateIds": ["msg-user-1"]
            }
            """);

        assertTrue(result.isSuccess());
        assertFalse(result.isDegraded());
        assertNotNull(result.getStateDelta());
        assertTrue(result.getStateDelta().isEmpty());
        assertEquals(List.of("msg-user-1"), result.getSourceCandidateIds());
    }

    @Test
    void invalidJsonShouldReturnDegradedResult() {
        StateDeltaExtractionResult result = parser.parse("{not-json");

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("Invalid StateDelta JSON"));
    }

    @Test
    void schemaGuardShouldRejectOldUpsertField() {
        StateDeltaExtractionResult result = parser.parse("""
            {
              "upsert": [],
              "sourceCandidateIds": []
            }
            """);

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("upsert"));
    }

    @Test
    void schemaGuardShouldRejectDebugField() {
        StateDeltaExtractionResult result = parser.parse(
            PromptContractTestSupport.fixture("prompt-fixtures/state-delta/invalid-extra-field-output.json")
        );

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("debug"));
    }

    @Test
    void schemaGuardShouldRejectLocaleField() {
        StateDeltaExtractionResult result = parser.parse("""
            {
              "locale": "zh-CN",
              "sourceCandidateIds": []
            }
            """);

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("locale"));
    }

    @Test
    void schemaGuardShouldRejectNestedEvidenceIds() {
        StateDeltaExtractionResult result = parser.parse(
            PromptContractTestSupport.fixture("prompt-fixtures/state-delta/invalid-nested-extra-field-output.json")
        );

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("evidenceIds"));
    }

    @Test
    void schemaGuardShouldRejectEmptyAppendRecords() {
        assertRejected(
            PromptContractTestSupport.fixture("prompt-fixtures/state-delta/invalid-empty-confirmed-fact-output.json"),
            "confirmedFactsAppend"
        );
        assertRejected(
            PromptContractTestSupport.fixture("prompt-fixtures/state-delta/invalid-empty-constraint-output.json"),
            "constraintsAppend"
        );
        assertRejected(
            PromptContractTestSupport.fixture("prompt-fixtures/state-delta/invalid-empty-decision-output.json"),
            "decisionsAppend"
        );
        assertRejected(
            PromptContractTestSupport.fixture("prompt-fixtures/state-delta/invalid-empty-open-loop-output.json"),
            "openLoopsAppend"
        );
        assertRejected(
            PromptContractTestSupport.fixture("prompt-fixtures/state-delta/invalid-empty-tool-outcome-output.json"),
            "recentToolOutcomesAppend"
        );
    }

    @Test
    void parserShouldRejectBlankAppendRecordContentAfterSchemaGuardPasses() {
        assertRejected("{\"confirmedFactsAppend\":[{\"factId\":\"f1\",\"content\":\"   \"}]}", "content");
        assertRejected("{\"constraintsAppend\":[{\"constraintId\":\"c1\",\"content\":\"   \"}]}", "content");
        assertRejected("{\"decisionsAppend\":[{\"decisionId\":\"d1\",\"content\":\"   \"}]}", "content");
        assertRejected("{\"openLoopsAppend\":[{\"loopId\":\"l1\",\"content\":\"   \"}]}", "content");
        assertRejected("{\"recentToolOutcomesAppend\":[{\"digestId\":\"r1\",\"summary\":\"   \"}]}", "summary");
    }

    @Test
    void parserShouldRejectBlankTaskGoalOverride() {
        assertRejected(
            PromptContractTestSupport.fixture("prompt-fixtures/state-delta/invalid-blank-task-goal-output.json"),
            "taskGoalOverride"
        );
        assertRejected(
            PromptContractTestSupport.fixture("prompt-fixtures/state-delta/invalid-whitespace-task-goal-output.json"),
            "taskGoalOverride"
        );
    }

    @Test
    void parserShouldAcceptNonBlankTaskGoalOverride() {
        StateDeltaExtractionResult result = parser.parse(
            PromptContractTestSupport.fixture("prompt-fixtures/state-delta/valid-task-goal-output.json")
        );

        assertTrue(result.isSuccess());
        assertFalse(result.isDegraded());
        assertNotNull(result.getStateDelta());
        assertEquals("new goal", result.getStateDelta().getTaskGoalOverride());
        assertFalse(result.getStateDelta().isEmpty());
    }

    @Test
    void legalSingleAppendRecordOutputsShouldParseSuccessfully() {
        assertSuccess("{\"confirmedFactsAppend\":[{\"factId\":\"f1\",\"content\":\"fact\"}]}");
        assertSuccess("{\"constraintsAppend\":[{\"constraintId\":\"c1\",\"content\":\"constraint\"}]}");
        assertSuccess("{\"decisionsAppend\":[{\"decisionId\":\"d1\",\"content\":\"decision\"}]}");
        assertSuccess("{\"openLoopsAppend\":[{\"loopId\":\"l1\",\"content\":\"loop\"}]}");
        assertSuccess("{\"recentToolOutcomesAppend\":[{\"digestId\":\"r1\",\"summary\":\"tool outcome\"}]}");
    }

    private void assertRejected(String json, String expectedReasonPart) {
        StateDeltaExtractionResult result = parser.parse(json);

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains(expectedReasonPart));
    }

    private void assertSuccess(String json) {
        StateDeltaExtractionResult result = parser.parse(json);

        assertTrue(result.isSuccess());
        assertFalse(result.isDegraded());
        assertFalse(result.getStateDelta().isEmpty());
    }
}

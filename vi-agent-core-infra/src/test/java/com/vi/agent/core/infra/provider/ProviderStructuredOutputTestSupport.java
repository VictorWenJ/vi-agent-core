package com.vi.agent.core.infra.provider;

import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.StructuredLlmOutputTarget;

/**
 * Provider structured output 测试辅助方法。
 */
public final class ProviderStructuredOutputTestSupport {

    private ProviderStructuredOutputTestSupport() {
    }

    public static StructuredLlmOutputContract strictCompatibleStateDeltaContract() {
        return stateDeltaContract("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "x-structuredOutputContractKey": "state_delta_output",
              "x-outputTarget": "state_delta_extraction_result",
              "x-description": "Strict compatible state delta.",
              "additionalProperties": false,
              "properties": {
                "taskGoalOverride": {
                  "type": "string",
                  "minLength": 1
                }
              }
            }
            """);
    }

    public static StructuredLlmOutputContract nonStrictCompatibleStateDeltaContract() {
        return stateDeltaContract("""
            {
              "$schema": "https://json-schema.org/draft/2020-12/schema",
              "type": "object",
              "x-structuredOutputContractKey": "state_delta_output",
              "x-outputTarget": "state_delta_extraction_result",
              "x-description": "Non strict state delta.",
              "additionalProperties": false,
              "properties": {
                "taskGoalOverride": {
                  "type": ["string", "null"],
                  "minLength": 1
                }
              }
            }
            """);
    }

    public static StructuredLlmOutputContract oneOfSummaryContract() {
        return StructuredLlmOutputContract.builder()
            .structuredOutputContractKey(StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT)
            .outputTarget(StructuredLlmOutputTarget.CONVERSATION_SUMMARY_EXTRACTION_RESULT)
            .description("Summary with oneOf.")
            .schemaJson("""
                {
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "type": "object",
                  "x-structuredOutputContractKey": "conversation_summary_output",
                  "x-outputTarget": "conversation_summary_extraction_result",
                  "x-description": "Summary.",
                  "additionalProperties": false,
                  "properties": {
                    "summaryText": { "type": "string", "minLength": 1 },
                    "skipped": { "type": "boolean" }
                  },
                  "oneOf": [
                    { "required": ["summaryText"] },
                    { "properties": { "skipped": { "const": true } }, "required": ["skipped"] }
                  ]
                }
                """)
            .build();
    }

    private static StructuredLlmOutputContract stateDeltaContract(String schemaJson) {
        return StructuredLlmOutputContract.builder()
            .structuredOutputContractKey(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT)
            .outputTarget(StructuredLlmOutputTarget.STATE_DELTA_EXTRACTION_RESULT)
            .description("State delta.")
            .schemaJson(schemaJson)
            .build();
    }
}

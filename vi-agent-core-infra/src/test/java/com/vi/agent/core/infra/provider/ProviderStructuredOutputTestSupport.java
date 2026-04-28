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
                  "type": "string"
                }
              },
              "required": ["taskGoalOverride"]
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
              },
              "required": ["taskGoalOverride"]
            }
            """);
    }

    public static StructuredLlmOutputContract missingRequiredStrictCandidateContract() {
        return stateDeltaContract("""
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "taskGoalOverride": { "type": "string" }
              }
            }
            """);
    }

    public static StructuredLlmOutputContract extraRequiredStrictCandidateContract() {
        return stateDeltaContract("""
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "taskGoalOverride": { "type": "string" }
              },
              "required": ["taskGoalOverride", "unknownField"]
            }
            """);
    }

    public static StructuredLlmOutputContract missingAdditionalPropertiesContract() {
        return stateDeltaContract("""
            {
              "type": "object",
              "properties": {
                "taskGoalOverride": { "type": "string" }
              },
              "required": ["taskGoalOverride"]
            }
            """);
    }

    public static StructuredLlmOutputContract stringMinLengthContract() {
        return stateDeltaContract("""
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "taskGoalOverride": { "type": "string", "minLength": 1 }
              },
              "required": ["taskGoalOverride"]
            }
            """);
    }

    public static StructuredLlmOutputContract stringMaxLengthContract() {
        return stateDeltaContract("""
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "taskGoalOverride": { "type": "string", "maxLength": 64 }
              },
              "required": ["taskGoalOverride"]
            }
            """);
    }

    public static StructuredLlmOutputContract arrayMinItemsContract() {
        return stateDeltaContract("""
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "sourceCandidateIds": {
                  "type": "array",
                  "minItems": 1,
                  "items": { "type": "string" }
                }
              },
              "required": ["sourceCandidateIds"]
            }
            """);
    }

    public static StructuredLlmOutputContract arrayMaxItemsContract() {
        return stateDeltaContract("""
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "sourceCandidateIds": {
                  "type": "array",
                  "maxItems": 5,
                  "items": { "type": "string" }
                }
              },
              "required": ["sourceCandidateIds"]
            }
            """);
    }

    public static StructuredLlmOutputContract nestedObjectMissingRequiredContract() {
        return stateDeltaContract("""
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "phaseStatePatch": {
                  "type": "object",
                  "additionalProperties": false,
                  "properties": {
                    "summaryEnabled": { "type": "boolean" }
                  }
                }
              },
              "required": ["phaseStatePatch"]
            }
            """);
    }

    public static StructuredLlmOutputContract anyOfContract() {
        return stateDeltaContract("""
            {
              "type": "object",
              "additionalProperties": false,
              "properties": {
                "taskGoalOverride": { "type": "string" }
              },
              "required": ["taskGoalOverride"],
              "anyOf": [
                { "required": ["taskGoalOverride"] }
              ]
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

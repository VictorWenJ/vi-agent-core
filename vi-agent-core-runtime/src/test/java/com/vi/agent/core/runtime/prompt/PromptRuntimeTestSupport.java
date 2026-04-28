package com.vi.agent.core.runtime.prompt;

import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.prompt.AbstractPromptTemplate;
import com.vi.agent.core.model.prompt.ConversationSummaryExtractPromptTemplate;
import com.vi.agent.core.model.prompt.ConversationSummaryRenderPromptTemplate;
import com.vi.agent.core.model.prompt.PromptInputPlacement;
import com.vi.agent.core.model.prompt.PromptInputTrustLevel;
import com.vi.agent.core.model.prompt.PromptInputVariable;
import com.vi.agent.core.model.prompt.PromptInputVariableType;
import com.vi.agent.core.model.prompt.PromptMessageTemplate;
import com.vi.agent.core.model.prompt.RuntimeInstructionRenderPromptTemplate;
import com.vi.agent.core.model.prompt.SessionStateRenderPromptTemplate;
import com.vi.agent.core.model.prompt.StateDeltaExtractPromptTemplate;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.SystemPromptKey;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Prompt runtime 单元测试辅助工厂。
 */
public final class PromptRuntimeTestSupport {

    /** 测试 catalog 修订标识。 */
    public static final String CATALOG_REVISION = "test-catalog-revision";

    private PromptRuntimeTestSupport() {
    }

    /**
     * 构造测试用 PromptRenderer。
     */
    public static PromptRenderer promptRenderer() {
        return new PromptRenderer(systemPromptRegistry());
    }

    /**
     * 构造测试用系统 prompt 注册表。
     */
    public static SystemPromptRegistry systemPromptRegistry() {
        Map<SystemPromptKey, AbstractPromptTemplate> templates = new EnumMap<>(SystemPromptKey.class);
        templates.put(SystemPromptKey.RUNTIME_INSTRUCTION_RENDER, runtimeInstructionTemplate());
        templates.put(SystemPromptKey.SESSION_STATE_RENDER, sessionStateTemplate());
        templates.put(SystemPromptKey.CONVERSATION_SUMMARY_RENDER, summaryRenderTemplate());
        templates.put(SystemPromptKey.STATE_DELTA_EXTRACT, stateDeltaExtractTemplate());
        templates.put(SystemPromptKey.CONVERSATION_SUMMARY_EXTRACT, summaryExtractTemplate());

        Map<StructuredLlmOutputContractKey, StructuredLlmOutputContract> contracts = new EnumMap<>(StructuredLlmOutputContractKey.class);
        contracts.put(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT, PromptContractTestSupport.stateDeltaContract());
        contracts.put(StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT, PromptContractTestSupport.conversationSummaryContract());

        Map<SystemPromptKey, String> templateHashes = new EnumMap<>(SystemPromptKey.class);
        Map<SystemPromptKey, String> manifestHashes = new EnumMap<>(SystemPromptKey.class);
        for (SystemPromptKey promptKey : SystemPromptKey.values()) {
            templateHashes.put(promptKey, "template-hash-" + promptKey.getValue());
            manifestHashes.put(promptKey, "manifest-hash-" + promptKey.getValue());
        }
        Map<StructuredLlmOutputContractKey, String> contractHashes = new EnumMap<>(StructuredLlmOutputContractKey.class);
        contractHashes.put(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT, "contract-hash-state");
        contractHashes.put(StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT, "contract-hash-summary");

        return new DefaultSystemPromptRegistry(
            templates,
            contracts,
            templateHashes,
            manifestHashes,
            contractHashes,
            CATALOG_REVISION
        );
    }

    private static RuntimeInstructionRenderPromptTemplate runtimeInstructionTemplate() {
        return new RuntimeInstructionRenderPromptTemplate(
            "Runtime controls: {{agentMode}} / {{workingMode}} / {{phaseStateText}}",
            List.of(
                trusted("agentMode", PromptInputVariableType.ENUM, true, null),
                trusted("workingMode", PromptInputVariableType.ENUM, false, "general"),
                trusted("phaseStateText", PromptInputVariableType.TEXT, false, "")
            ),
            "runtime instruction"
        );
    }

    private static SessionStateRenderPromptTemplate sessionStateTemplate() {
        return new SessionStateRenderPromptTemplate(
            """
                Session state metadata:
                - stateVersion: {{stateVersion}}
                [BEGIN_UNTRUSTED_SESSION_STATE_TEXT]
                {{sessionStateText}}
                [END_UNTRUSTED_SESSION_STATE_TEXT]
                """,
            List.of(
                trusted("stateVersion", PromptInputVariableType.NUMBER, true, null),
                untrusted("sessionStateText", PromptInputVariableType.TEXT, true)
            ),
            "session state"
        );
    }

    private static ConversationSummaryRenderPromptTemplate summaryRenderTemplate() {
        return new ConversationSummaryRenderPromptTemplate(
            """
                Conversation summary metadata:
                - summaryVersion: {{summaryVersion}}
                [BEGIN_UNTRUSTED_CONVERSATION_SUMMARY]
                {{summaryText}}
                [END_UNTRUSTED_CONVERSATION_SUMMARY]
                """,
            List.of(
                trusted("summaryVersion", PromptInputVariableType.NUMBER, true, null),
                untrusted("summaryText", PromptInputVariableType.TEXT, true)
            ),
            "conversation summary"
        );
    }

    private static StateDeltaExtractPromptTemplate stateDeltaExtractTemplate() {
        return new StateDeltaExtractPromptTemplate(
            List.of(
                PromptMessageTemplate.builder()
                    .order(0)
                    .role(MessageRole.SYSTEM)
                    .contentTemplate("state system")
                    .build(),
                PromptMessageTemplate.builder()
                    .order(1)
                    .role(MessageRole.USER)
                    .contentTemplate(extractUserTemplate("currentStateJson", "conversationSummaryText"))
                    .build()
            ),
            extractVariables("currentStateJson", "conversationSummaryText"),
            "state delta extract"
        );
    }

    private static ConversationSummaryExtractPromptTemplate summaryExtractTemplate() {
        return new ConversationSummaryExtractPromptTemplate(
            List.of(
                PromptMessageTemplate.builder()
                    .order(0)
                    .role(MessageRole.SYSTEM)
                    .contentTemplate("summary system")
                    .build(),
                PromptMessageTemplate.builder()
                    .order(1)
                    .role(MessageRole.USER)
                    .contentTemplate(extractUserTemplate("latestStateJson", "previousSummaryText"))
                    .build()
            ),
            extractVariables("latestStateJson", "previousSummaryText"),
            "summary extract"
        );
    }

    private static String extractUserTemplate(String stateVariable, String summaryVariable) {
        return """
            Metadata:
            - conversationId: {{conversationId}}
            - sessionId: {{sessionId}}
            - turnId: {{turnId}}
            - runId: {{runId}}
            - traceId: {{traceId}}
            - agentMode: {{agentMode}}
            - workingContextSnapshotId: {{workingContextSnapshotId}}
            [BEGIN_UNTRUSTED_SESSION_STATE_JSON]
            {{%s}}
            [END_UNTRUSTED_SESSION_STATE_JSON]
            [BEGIN_UNTRUSTED_CONVERSATION_SUMMARY]
            {{%s}}
            [END_UNTRUSTED_CONVERSATION_SUMMARY]
            [BEGIN_UNTRUSTED_CURRENT_TURN_MESSAGES]
            {{turnMessagesText}}
            [END_UNTRUSTED_CURRENT_TURN_MESSAGES]
            """.formatted(stateVariable, summaryVariable);
    }

    private static List<PromptInputVariable> extractVariables(String stateVariable, String summaryVariable) {
        return List.of(
            trusted("conversationId", PromptInputVariableType.TEXT, false, ""),
            trusted("sessionId", PromptInputVariableType.TEXT, true, null),
            trusted("turnId", PromptInputVariableType.TEXT, true, null),
            trusted("runId", PromptInputVariableType.TEXT, true, null),
            trusted("traceId", PromptInputVariableType.TEXT, false, ""),
            trusted("agentMode", PromptInputVariableType.ENUM, false, "general"),
            trusted("workingContextSnapshotId", PromptInputVariableType.TEXT, false, ""),
            untrusted(stateVariable, PromptInputVariableType.JSON, true),
            untrusted(summaryVariable, PromptInputVariableType.TEXT, false),
            untrusted("turnMessagesText", PromptInputVariableType.TEXT, true)
        );
    }

    private static PromptInputVariable trusted(
        String name,
        PromptInputVariableType type,
        boolean required,
        String defaultValue
    ) {
        return PromptInputVariable.builder()
            .variableName(name)
            .variableType(type)
            .trustLevel(PromptInputTrustLevel.TRUSTED_CONTROL)
            .placement(PromptInputPlacement.METADATA_BLOCK)
            .required(required)
            .description(name)
            .defaultValue(defaultValue)
            .build();
    }

    private static PromptInputVariable untrusted(String name, PromptInputVariableType type, boolean required) {
        return PromptInputVariable.builder()
            .variableName(name)
            .variableType(type)
            .trustLevel(PromptInputTrustLevel.UNTRUSTED_DATA)
            .placement(PromptInputPlacement.DATA_BLOCK)
            .required(required)
            .maxChars(12000)
            .truncateMarker("[TRUNCATED]")
            .description(name)
            .defaultValue(required ? null : "")
            .build();
    }
}

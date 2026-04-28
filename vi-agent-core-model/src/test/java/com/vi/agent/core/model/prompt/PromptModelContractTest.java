package com.vi.agent.core.model.prompt;

import com.vi.agent.core.model.message.MessageRole;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptModelContractTest {

    @Test
    void enumsShouldExposeStableValueAndChineseDescription() throws Exception {
        assertEnumContract(SystemPromptKey.class);
        assertEnumContract(PromptPurpose.class);
        assertEnumContract(PromptRenderOutputType.class);
        assertEnumContract(PromptInputVariableType.class);
        assertEnumContract(PromptInputTrustLevel.class);
        assertEnumContract(PromptInputPlacement.class);
        assertEnumContract(StructuredLlmOutputContractKey.class);
        assertEnumContract(StructuredLlmOutputTarget.class);
        assertEnumContract(StructuredLlmOutputMode.class);

        assertEquals("runtime_instruction_render", SystemPromptKey.RUNTIME_INSTRUCTION_RENDER.getValue());
        assertEquals("session_state_render", SystemPromptKey.SESSION_STATE_RENDER.getValue());
        assertEquals("conversation_summary_render", SystemPromptKey.CONVERSATION_SUMMARY_RENDER.getValue());
        assertEquals("state_delta_extract", SystemPromptKey.STATE_DELTA_EXTRACT.getValue());
        assertEquals("conversation_summary_extract", SystemPromptKey.CONVERSATION_SUMMARY_EXTRACT.getValue());
        assertEquals("state_delta_output", StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT.getValue());
        assertEquals("conversation_summary_output", StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT.getValue());
        assertEquals("state_delta_extraction_result", StructuredLlmOutputTarget.STATE_DELTA_EXTRACTION_RESULT.getValue());
        assertEquals("conversation_summary_extraction_result", StructuredLlmOutputTarget.CONVERSATION_SUMMARY_EXTRACTION_RESULT.getValue());
        assertEquals("strict_tool_call", StructuredLlmOutputMode.STRICT_TOOL_CALL.getValue());
        assertEquals("json_schema_response_format", StructuredLlmOutputMode.JSON_SCHEMA_RESPONSE_FORMAT.getValue());
        assertEquals("json_object", StructuredLlmOutputMode.JSON_OBJECT.getValue());
    }

    @Test
    void promptInputVariableShouldExposeSafetyBoundaryFields() {
        PromptInputVariable variable = PromptInputVariable.builder()
            .variableName("turnMessagesText")
            .variableType(PromptInputVariableType.TEXT)
            .trustLevel(PromptInputTrustLevel.UNTRUSTED_DATA)
            .placement(PromptInputPlacement.DATA_BLOCK)
            .required(true)
            .maxChars(12000)
            .truncateMarker("[TRUNCATED]")
            .description("当前 turn 消息文本")
            .defaultValue("")
            .build();

        assertEquals("turnMessagesText", variable.getVariableName());
        assertEquals(PromptInputTrustLevel.UNTRUSTED_DATA, variable.getTrustLevel());
        assertEquals(PromptInputPlacement.DATA_BLOCK, variable.getPlacement());
        assertEquals(12000, variable.getMaxChars());
        assertEquals("[TRUNCATED]", variable.getTruncateMarker());
    }

    @Test
    void structuredContractShouldCompileInP2E1WithoutRuntimeGuard() {
        StructuredLlmOutputContract contract = StructuredLlmOutputContract.builder()
            .structuredOutputContractKey(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT)
            .outputTarget(StructuredLlmOutputTarget.STATE_DELTA_EXTRACTION_RESULT)
            .schemaJson("{\"type\":\"object\"}")
            .description("状态增量结构化输出契约")
            .build();

        assertEquals(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT, contract.getStructuredOutputContractKey());
        assertEquals(StructuredLlmOutputTarget.STATE_DELTA_EXTRACTION_RESULT, contract.getOutputTarget());
        assertTrue(contract.getSchemaJson().contains("\"type\":\"object\""));
    }

    @Test
    void concreteTemplatesShouldFixTheirSystemIdentity() {
        PromptInputVariable variable = PromptInputVariable.builder()
            .variableName("agentMode")
            .variableType(PromptInputVariableType.ENUM)
            .trustLevel(PromptInputTrustLevel.TRUSTED_CONTROL)
            .placement(PromptInputPlacement.INSTRUCTION_BLOCK)
            .required(true)
            .description("当前 AgentMode")
            .build();

        RuntimeInstructionRenderPromptTemplate runtimeTemplate =
            new RuntimeInstructionRenderPromptTemplate("mode={{agentMode}}", List.of(variable), "运行指令");
        assertEquals(SystemPromptKey.RUNTIME_INSTRUCTION_RENDER, runtimeTemplate.getPromptKey());
        assertEquals(PromptPurpose.RUNTIME_INSTRUCTION_RENDER, runtimeTemplate.getPurpose());
        assertEquals(PromptRenderOutputType.TEXT, runtimeTemplate.getRenderOutputType());
        assertEquals(null, runtimeTemplate.getStructuredOutputContractKey());

        StateDeltaExtractPromptTemplate extractTemplate = new StateDeltaExtractPromptTemplate(
            List.of(PromptMessageTemplate.builder()
                .order(1)
                .role(MessageRole.SYSTEM)
                .contentTemplate("system")
                .build()),
            List.of(),
            "状态抽取"
        );
        assertEquals(SystemPromptKey.STATE_DELTA_EXTRACT, extractTemplate.getPromptKey());
        assertEquals(PromptPurpose.STATE_DELTA_EXTRACTION, extractTemplate.getPurpose());
        assertEquals(PromptRenderOutputType.CHAT_MESSAGES, extractTemplate.getRenderOutputType());
        assertEquals(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT, extractTemplate.getStructuredOutputContractKey());
    }

    @Test
    void abstractPromptTemplateShouldCopyCollectionsAsImmutable() {
        PromptInputVariable variable = PromptInputVariable.builder()
            .variableName("summaryText")
            .variableType(PromptInputVariableType.TEXT)
            .trustLevel(PromptInputTrustLevel.UNTRUSTED_DATA)
            .placement(PromptInputPlacement.DATA_BLOCK)
            .required(true)
            .maxChars(8000)
            .truncateMarker("[TRUNCATED]")
            .description("摘要正文")
            .build();

        ConversationSummaryRenderPromptTemplate template =
            new ConversationSummaryRenderPromptTemplate("summary={{summaryText}}", List.of(variable), "摘要渲染");

        assertThrows(UnsupportedOperationException.class,
            () -> template.getInputVariables().add(variable));
        assertTrue(template.getMessageTemplates().isEmpty());
    }

    @Test
    void promptRenderMetadataShouldContainAuditHashesAndCatalogRevision() {
        PromptRenderMetadata metadata = PromptRenderMetadata.builder()
            .promptKey(SystemPromptKey.STATE_DELTA_EXTRACT)
            .purpose(PromptPurpose.STATE_DELTA_EXTRACTION)
            .structuredOutputContractKey(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT)
            .templateContentHash("template-hash")
            .manifestContentHash("manifest-hash")
            .contractContentHash("contract-hash")
            .catalogRevision("local-dev")
            .renderedVariableName("sessionId")
            .build();

        assertEquals(SystemPromptKey.STATE_DELTA_EXTRACT, metadata.getPromptKey());
        assertEquals("template-hash", metadata.getTemplateContentHash());
        assertEquals("manifest-hash", metadata.getManifestContentHash());
        assertEquals("contract-hash", metadata.getContractContentHash());
        assertEquals("local-dev", metadata.getCatalogRevision());
        assertEquals(List.of("sessionId"), metadata.getRenderedVariableNames());
    }

    private static <E extends Enum<E>> void assertEnumContract(Class<E> enumClass) throws Exception {
        Constructor<?>[] constructors = enumClass.getDeclaredConstructors();
        assertTrue(constructors.length > 0, enumClass.getSimpleName() + " should have enum constructor");
        for (E enumConstant : enumClass.getEnumConstants()) {
            Object value = enumClass.getMethod("getValue").invoke(enumConstant);
            Object description = enumClass.getMethod("getDescription").invoke(enumConstant);
            assertInstanceOf(String.class, value);
            assertInstanceOf(String.class, description);
            assertFalse(((String) value).isBlank(), enumClass.getSimpleName() + "." + enumConstant.name() + " value");
            assertTrue(containsChinese((String) description),
                enumClass.getSimpleName() + "." + enumConstant.name() + " description should contain Chinese");
        }
        assertNotNull(enumClass.getDeclaredField("value"));
        assertNotNull(enumClass.getDeclaredField("description"));
    }

    private static boolean containsChinese(String text) {
        return text != null && text.codePoints().anyMatch(cp -> cp >= 0x4E00 && cp <= 0x9FFF);
    }
}

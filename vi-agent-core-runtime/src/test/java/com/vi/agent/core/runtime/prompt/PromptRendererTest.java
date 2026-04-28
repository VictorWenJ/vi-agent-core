package com.vi.agent.core.runtime.prompt;

import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.prompt.AbstractPromptTemplate;
import com.vi.agent.core.model.prompt.PromptInputPlacement;
import com.vi.agent.core.model.prompt.PromptInputTrustLevel;
import com.vi.agent.core.model.prompt.PromptInputVariable;
import com.vi.agent.core.model.prompt.PromptInputVariableType;
import com.vi.agent.core.model.prompt.PromptMessageTemplate;
import com.vi.agent.core.model.prompt.PromptPurpose;
import com.vi.agent.core.model.prompt.PromptRenderOutputType;
import com.vi.agent.core.model.prompt.RuntimeInstructionRenderPromptTemplate;
import com.vi.agent.core.model.prompt.StateDeltaExtractPromptTemplate;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.StructuredLlmOutputTarget;
import com.vi.agent.core.model.prompt.SystemPromptKey;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptRendererTest {

    @Test
    void shouldRenderTextTemplate() {
        PromptRenderer renderer = new PromptRenderer(registry(textTemplate("Hello {{name}}", variable("name"))));

        PromptRenderResult result = renderer.render(PromptRenderRequest.builder()
            .promptKey(SystemPromptKey.RUNTIME_INSTRUCTION_RENDER)
            .variable("name", "Alice")
            .build());

        TextPromptRenderResult textResult = assertInstanceOf(TextPromptRenderResult.class, result);
        assertEquals("Hello Alice", textResult.getRenderedText());
        assertEquals(SystemPromptKey.RUNTIME_INSTRUCTION_RENDER, textResult.getPromptKey());
        assertEquals(PromptPurpose.RUNTIME_INSTRUCTION_RENDER, textResult.getPurpose());
        assertEquals(PromptRenderOutputType.TEXT, textResult.getRenderOutputType());
        assertEquals(SystemPromptKey.RUNTIME_INSTRUCTION_RENDER, textResult.getMetadata().getPromptKey());
    }

    @Test
    void shouldRenderChatMessagesTemplateWithContractKey() {
        PromptRenderer renderer = new PromptRenderer(registry(chatTemplate(variable("turnMessagesText"))));

        PromptRenderResult result = renderer.render(PromptRenderRequest.builder()
            .promptKey(SystemPromptKey.STATE_DELTA_EXTRACT)
            .variable("turnMessagesText", "hello")
            .build());

        ChatMessagesPromptRenderResult chatResult = assertInstanceOf(ChatMessagesPromptRenderResult.class, result);
        assertEquals(SystemPromptKey.STATE_DELTA_EXTRACT, chatResult.getPromptKey());
        assertEquals(PromptPurpose.STATE_DELTA_EXTRACTION, chatResult.getPurpose());
        assertEquals(PromptRenderOutputType.CHAT_MESSAGES, chatResult.getRenderOutputType());
        assertEquals(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT, chatResult.getStructuredOutputContractKey());
        assertEquals(2, chatResult.getRenderedMessages().size());
        assertEquals(MessageRole.USER, chatResult.getRenderedMessages().get(1).getRole());
        assertTrue(chatResult.getRenderedMessages().get(1).getRenderedContent().contains("hello"));
    }

    @Test
    void shouldFailWhenRequiredVariableIsMissing() {
        PromptRenderer renderer = new PromptRenderer(registry(textTemplate("Hello {{name}}", variable("name"))));

        assertThrows(PromptRenderException.class, () -> renderer.render(PromptRenderRequest.builder()
            .promptKey(SystemPromptKey.RUNTIME_INSTRUCTION_RENDER)
            .build()));
    }

    @Test
    void shouldFailWhenRequestContainsUndeclaredVariable() {
        PromptRenderer renderer = new PromptRenderer(registry(textTemplate("Hello {{name}}", variable("name"))));

        assertThrows(PromptRenderException.class, () -> renderer.render(PromptRenderRequest.builder()
            .promptKey(SystemPromptKey.RUNTIME_INSTRUCTION_RENDER)
            .variable("name", "Alice")
            .variable("unknown", "value")
            .build()));
    }

    @Test
    void shouldFailWhenTemplateContainsUndeclaredPlaceholder() {
        PromptRenderer renderer = new PromptRenderer(registry(textTemplate("Hello {{missing}}", variable("name"))));

        assertThrows(PromptRenderException.class, () -> renderer.render(PromptRenderRequest.builder()
            .promptKey(SystemPromptKey.RUNTIME_INSTRUCTION_RENDER)
            .variable("name", "Alice")
            .build()));
    }

    @Test
    void shouldNotRecursivelyReplaceVariableValue() {
        PromptRenderer renderer = new PromptRenderer(registry(textTemplate("Hello {{name}}", variable("name"))));

        TextPromptRenderResult result = (TextPromptRenderResult) renderer.render(PromptRenderRequest.builder()
            .promptKey(SystemPromptKey.RUNTIME_INSTRUCTION_RENDER)
            .variable("name", "{{evil}}")
            .build());

        assertEquals("Hello {{evil}}", result.getRenderedText());
    }

    @Test
    void shouldTruncateUntrustedDataWithMarker() {
        PromptInputVariable variable = PromptInputVariable.builder()
            .variableName("payload")
            .variableType(PromptInputVariableType.TEXT)
            .trustLevel(PromptInputTrustLevel.UNTRUSTED_DATA)
            .placement(PromptInputPlacement.DATA_BLOCK)
            .required(true)
            .maxChars(3)
            .truncateMarker("[TRUNCATED]")
            .description("载荷")
            .build();
        PromptRenderer renderer = new PromptRenderer(registry(textTemplate("Data {{payload}}", variable)));

        TextPromptRenderResult result = (TextPromptRenderResult) renderer.render(PromptRenderRequest.builder()
            .promptKey(SystemPromptKey.RUNTIME_INSTRUCTION_RENDER)
            .variable("payload", "abcdef")
            .build());

        assertEquals("Data abc[TRUNCATED]", result.getRenderedText());
    }

    @Test
    void shouldFailWhenUntrustedDataIsInstructionBlock() {
        PromptInputVariable variable = PromptInputVariable.builder()
            .variableName("payload")
            .variableType(PromptInputVariableType.TEXT)
            .trustLevel(PromptInputTrustLevel.UNTRUSTED_DATA)
            .placement(PromptInputPlacement.INSTRUCTION_BLOCK)
            .required(true)
            .maxChars(10)
            .truncateMarker("[TRUNCATED]")
            .description("载荷")
            .build();
        PromptRenderer renderer = new PromptRenderer(registry(textTemplate("Data {{payload}}", variable)));

        assertThrows(PromptRenderException.class, () -> renderer.render(PromptRenderRequest.builder()
            .promptKey(SystemPromptKey.RUNTIME_INSTRUCTION_RENDER)
            .variable("payload", "value")
            .build()));
    }

    @Test
    void shouldFillRenderMetadata() {
        PromptRenderer renderer = new PromptRenderer(registry(textTemplate("Hello {{name}}", variable("name"))));

        TextPromptRenderResult result = (TextPromptRenderResult) renderer.render(PromptRenderRequest.builder()
            .promptKey(SystemPromptKey.RUNTIME_INSTRUCTION_RENDER)
            .variable("name", "Alice")
            .build());

        assertEquals(SystemPromptKey.RUNTIME_INSTRUCTION_RENDER, result.getPromptKey());
        assertEquals(PromptPurpose.RUNTIME_INSTRUCTION_RENDER, result.getPurpose());
        assertEquals(PromptRenderOutputType.TEXT, result.getRenderOutputType());
        assertEquals(PromptPurpose.RUNTIME_INSTRUCTION_RENDER, result.getMetadata().getPurpose());
        assertEquals("template-hash", result.getMetadata().getTemplateContentHash());
        assertEquals("manifest-hash", result.getMetadata().getManifestContentHash());
        assertEquals("revision", result.getMetadata().getCatalogRevision());
        assertEquals(List.of("name"), result.getMetadata().getRenderedVariableNames());
    }

    private static RuntimeInstructionRenderPromptTemplate textTemplate(
        String content,
        PromptInputVariable variable
    ) {
        return new RuntimeInstructionRenderPromptTemplate(content, List.of(variable), "runtime");
    }

    private static StateDeltaExtractPromptTemplate chatTemplate(PromptInputVariable variable) {
        return new StateDeltaExtractPromptTemplate(
            List.of(
                PromptMessageTemplate.builder()
                    .order(1)
                    .role(MessageRole.SYSTEM)
                    .contentTemplate("system")
                    .build(),
                PromptMessageTemplate.builder()
                    .order(2)
                    .role(MessageRole.USER)
                    .contentTemplate("user {{turnMessagesText}}")
                    .build()
            ),
            List.of(variable),
            "state extract"
        );
    }

    private static PromptInputVariable variable(String variableName) {
        return PromptInputVariable.builder()
            .variableName(variableName)
            .variableType(PromptInputVariableType.TEXT)
            .trustLevel(PromptInputTrustLevel.TRUSTED_CONTROL)
            .placement(PromptInputPlacement.INSTRUCTION_BLOCK)
            .required(true)
            .description(variableName)
            .build();
    }

    private static DefaultSystemPromptRegistry registry(AbstractPromptTemplate template) {
        Map<SystemPromptKey, AbstractPromptTemplate> templates = new EnumMap<>(SystemPromptKey.class);
        templates.put(template.getPromptKey(), template);
        Map<SystemPromptKey, String> templateHashes = new EnumMap<>(SystemPromptKey.class);
        templateHashes.put(template.getPromptKey(), "template-hash");
        Map<SystemPromptKey, String> manifestHashes = new EnumMap<>(SystemPromptKey.class);
        manifestHashes.put(template.getPromptKey(), "manifest-hash");
        Map<StructuredLlmOutputContractKey, StructuredLlmOutputContract> contracts =
            new EnumMap<>(StructuredLlmOutputContractKey.class);
        Map<StructuredLlmOutputContractKey, String> contractHashes =
            new EnumMap<>(StructuredLlmOutputContractKey.class);
        if (template.getStructuredOutputContractKey() != null) {
            contracts.put(
                template.getStructuredOutputContractKey(),
                StructuredLlmOutputContract.builder()
                    .structuredOutputContractKey(template.getStructuredOutputContractKey())
                    .outputTarget(StructuredLlmOutputTarget.STATE_DELTA_EXTRACTION_RESULT)
                    .schemaJson("{}")
                    .description("contract")
                    .build()
            );
            contractHashes.put(template.getStructuredOutputContractKey(), "contract-hash");
        }
        return new DefaultSystemPromptRegistry(
            templates,
            contracts,
            templateHashes,
            manifestHashes,
            contractHashes,
            "revision"
        );
    }
}

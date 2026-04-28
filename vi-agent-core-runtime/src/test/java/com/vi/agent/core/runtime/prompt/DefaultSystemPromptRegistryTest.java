package com.vi.agent.core.runtime.prompt;

import com.vi.agent.core.model.prompt.AbstractPromptTemplate;
import com.vi.agent.core.model.prompt.PromptInputPlacement;
import com.vi.agent.core.model.prompt.PromptInputTrustLevel;
import com.vi.agent.core.model.prompt.PromptInputVariable;
import com.vi.agent.core.model.prompt.PromptInputVariableType;
import com.vi.agent.core.model.prompt.RuntimeInstructionRenderPromptTemplate;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.StructuredLlmOutputTarget;
import com.vi.agent.core.model.prompt.SystemPromptKey;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultSystemPromptRegistryTest {

    @Test
    void shouldKeepInternalCollectionsReadonly() {
        Map<SystemPromptKey, AbstractPromptTemplate> templates = new EnumMap<>(SystemPromptKey.class);
        templates.put(
            SystemPromptKey.RUNTIME_INSTRUCTION_RENDER,
            new RuntimeInstructionRenderPromptTemplate("hello {{name}}", List.of(variable()), "runtime")
        );
        Map<StructuredLlmOutputContractKey, StructuredLlmOutputContract> contracts =
            new EnumMap<>(StructuredLlmOutputContractKey.class);
        contracts.put(
            StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT,
            StructuredLlmOutputContract.builder()
                .structuredOutputContractKey(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT)
                .outputTarget(StructuredLlmOutputTarget.STATE_DELTA_EXTRACTION_RESULT)
                .schemaJson("{}")
                .description("contract")
                .build()
        );

        DefaultSystemPromptRegistry registry = new DefaultSystemPromptRegistry(
            templates,
            contracts,
            Map.of(SystemPromptKey.RUNTIME_INSTRUCTION_RENDER, "template-hash"),
            Map.of(SystemPromptKey.RUNTIME_INSTRUCTION_RENDER, "manifest-hash"),
            Map.of(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT, "contract-hash"),
            "revision"
        );
        templates.clear();

        assertEquals(
            SystemPromptKey.RUNTIME_INSTRUCTION_RENDER,
            registry.get(SystemPromptKey.RUNTIME_INSTRUCTION_RENDER).getPromptKey()
        );
        assertThrows(UnsupportedOperationException.class, () ->
            registry.templates().put(SystemPromptKey.SESSION_STATE_RENDER, registry.runtimeInstructionRenderTemplate())
        );
    }

    private static PromptInputVariable variable() {
        return PromptInputVariable.builder()
            .variableName("name")
            .variableType(PromptInputVariableType.TEXT)
            .trustLevel(PromptInputTrustLevel.TRUSTED_CONTROL)
            .placement(PromptInputPlacement.INSTRUCTION_BLOCK)
            .required(true)
            .description("名称")
            .build();
    }
}

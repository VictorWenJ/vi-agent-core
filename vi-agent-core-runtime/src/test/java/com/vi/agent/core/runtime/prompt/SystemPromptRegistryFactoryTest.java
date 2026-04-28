package com.vi.agent.core.runtime.prompt;

import com.vi.agent.core.model.port.SystemPromptCatalogRepository;
import com.vi.agent.core.model.prompt.AbstractPromptTemplate;
import com.vi.agent.core.model.prompt.ConversationSummaryExtractPromptTemplate;
import com.vi.agent.core.model.prompt.ConversationSummaryRenderPromptTemplate;
import com.vi.agent.core.model.prompt.PromptInputPlacement;
import com.vi.agent.core.model.prompt.PromptInputTrustLevel;
import com.vi.agent.core.model.prompt.PromptInputVariable;
import com.vi.agent.core.model.prompt.PromptInputVariableType;
import com.vi.agent.core.model.prompt.PromptMessageTemplate;
import com.vi.agent.core.model.prompt.PromptPurpose;
import com.vi.agent.core.model.prompt.PromptRenderOutputType;
import com.vi.agent.core.model.prompt.RuntimeInstructionRenderPromptTemplate;
import com.vi.agent.core.model.prompt.SessionStateRenderPromptTemplate;
import com.vi.agent.core.model.prompt.StateDeltaExtractPromptTemplate;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContract;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.StructuredLlmOutputTarget;
import com.vi.agent.core.model.prompt.SystemPromptKey;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SystemPromptRegistryFactoryTest {

    @Test
    void shouldCreateReadonlyRegistryFromCatalogRepository() {
        SystemPromptRegistry registry = new SystemPromptRegistryFactory(new StubRepository()).create();

        assertInstanceOf(
            RuntimeInstructionRenderPromptTemplate.class,
            registry.get(SystemPromptKey.RUNTIME_INSTRUCTION_RENDER)
        );
        assertInstanceOf(SessionStateRenderPromptTemplate.class, registry.getSessionStateRenderPromptTemplate());
        assertInstanceOf(
            ConversationSummaryRenderPromptTemplate.class,
            registry.getConversationSummaryRenderPromptTemplate()
        );
        assertInstanceOf(StateDeltaExtractPromptTemplate.class, registry.getStateDeltaExtractPromptTemplate());
        assertInstanceOf(
            ConversationSummaryExtractPromptTemplate.class,
            registry.getConversationSummaryExtractPromptTemplate()
        );
        assertEquals(
            StructuredLlmOutputTarget.STATE_DELTA_EXTRACTION_RESULT,
            registry.getStructuredLlmOutputContract(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT)
                .getOutputTarget()
        );
    }

    @Test
    void shouldFailFastWhenRepositoryMissesTemplate() {
        StubRepository repository = new StubRepository();
        repository.templates.remove(SystemPromptKey.SESSION_STATE_RENDER);

        assertThrows(IllegalStateException.class, () -> new SystemPromptRegistryFactory(repository).create());
    }

    @Test
    void shouldFailFastWhenExtractTemplateUsesDuplicatedContractKey() {
        StubRepository repository = new StubRepository();
        repository.templates.put(SystemPromptKey.CONVERSATION_SUMMARY_EXTRACT, new DuplicateContractTemplate());

        assertThrows(IllegalStateException.class, () -> new SystemPromptRegistryFactory(repository).create());
    }

    @Test
    void shouldFailFastWhenConcreteClassDoesNotMatchPromptKey() {
        StubRepository repository = new StubRepository();
        repository.templates.put(SystemPromptKey.SESSION_STATE_RENDER, new WrongConcreteClassTemplate());

        assertThrows(IllegalStateException.class, () -> new SystemPromptRegistryFactory(repository).create());
    }

    private static PromptInputVariable textVariable(String variableName) {
        return PromptInputVariable.builder()
            .variableName(variableName)
            .variableType(PromptInputVariableType.TEXT)
            .trustLevel(PromptInputTrustLevel.TRUSTED_CONTROL)
            .placement(PromptInputPlacement.INSTRUCTION_BLOCK)
            .required(true)
            .description(variableName)
            .build();
    }

    private static StructuredLlmOutputContract contract(
        StructuredLlmOutputContractKey contractKey,
        StructuredLlmOutputTarget outputTarget
    ) {
        return StructuredLlmOutputContract.builder()
            .structuredOutputContractKey(contractKey)
            .outputTarget(outputTarget)
            .schemaJson("{\"type\":\"object\"}")
            .description(contractKey.getDescription())
            .build();
    }

    private static List<PromptMessageTemplate> messages() {
        return List.of(
            PromptMessageTemplate.builder()
                .order(1)
                .contentTemplate("system")
                .build(),
            PromptMessageTemplate.builder()
                .order(2)
                .contentTemplate("user {{input}}")
                .build()
        );
    }

    private static final class StubRepository implements SystemPromptCatalogRepository {

        private final Map<SystemPromptKey, AbstractPromptTemplate> templates = new EnumMap<>(SystemPromptKey.class);

        private final Map<StructuredLlmOutputContractKey, StructuredLlmOutputContract> contracts =
            new EnumMap<>(StructuredLlmOutputContractKey.class);

        private StubRepository() {
            List<PromptInputVariable> variables = List.of(textVariable("input"));
            templates.put(
                SystemPromptKey.RUNTIME_INSTRUCTION_RENDER,
                new RuntimeInstructionRenderPromptTemplate("runtime {{input}}", variables, "runtime")
            );
            templates.put(
                SystemPromptKey.SESSION_STATE_RENDER,
                new SessionStateRenderPromptTemplate("state {{input}}", variables, "state")
            );
            templates.put(
                SystemPromptKey.CONVERSATION_SUMMARY_RENDER,
                new ConversationSummaryRenderPromptTemplate("summary {{input}}", variables, "summary")
            );
            templates.put(
                SystemPromptKey.STATE_DELTA_EXTRACT,
                new StateDeltaExtractPromptTemplate(messages(), variables, "state extract")
            );
            templates.put(
                SystemPromptKey.CONVERSATION_SUMMARY_EXTRACT,
                new ConversationSummaryExtractPromptTemplate(messages(), variables, "summary extract")
            );
            contracts.put(
                StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT,
                contract(
                    StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT,
                    StructuredLlmOutputTarget.STATE_DELTA_EXTRACTION_RESULT
                )
            );
            contracts.put(
                StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT,
                contract(
                    StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT,
                    StructuredLlmOutputTarget.CONVERSATION_SUMMARY_EXTRACTION_RESULT
                )
            );
        }

        @Override
        public Optional<AbstractPromptTemplate> findTemplate(SystemPromptKey promptKey) {
            return Optional.ofNullable(templates.get(promptKey));
        }

        @Override
        public Optional<StructuredLlmOutputContract> findContract(StructuredLlmOutputContractKey contractKey) {
            return Optional.ofNullable(contracts.get(contractKey));
        }

        @Override
        public String templateContentHash(SystemPromptKey promptKey) {
            return "template-" + promptKey.getValue();
        }

        @Override
        public String manifestContentHash(SystemPromptKey promptKey) {
            return "manifest-" + promptKey.getValue();
        }

        @Override
        public String contractContentHash(StructuredLlmOutputContractKey contractKey) {
            return "contract-" + contractKey.getValue();
        }

        @Override
        public String catalogRevision() {
            return "test-revision";
        }
    }

    private static final class DuplicateContractTemplate extends AbstractPromptTemplate {

        private DuplicateContractTemplate() {
            super(
                SystemPromptKey.CONVERSATION_SUMMARY_EXTRACT,
                PromptPurpose.CONVERSATION_SUMMARY_EXTRACTION,
                PromptRenderOutputType.CHAT_MESSAGES,
                "",
                messages(),
                List.of(textVariable("input")),
                StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT,
                "duplicate"
            );
        }
    }

    private static final class WrongConcreteClassTemplate extends AbstractPromptTemplate {

        private WrongConcreteClassTemplate() {
            super(
                SystemPromptKey.SESSION_STATE_RENDER,
                PromptPurpose.SESSION_STATE_RENDER,
                PromptRenderOutputType.TEXT,
                "state {{input}}",
                List.of(),
                List.of(textVariable("input")),
                null,
                "wrong concrete"
            );
        }
    }
}

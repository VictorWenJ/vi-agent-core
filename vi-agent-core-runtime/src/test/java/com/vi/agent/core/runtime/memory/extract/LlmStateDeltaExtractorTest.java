package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.common.id.InternalTaskMessageIdGenerator;
import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.llm.NormalizedStructuredLlmOutput;
import com.vi.agent.core.model.llm.ModelRequest;
import com.vi.agent.core.model.llm.ModelResponse;
import com.vi.agent.core.model.llm.StructuredOutputChannelResult;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.LlmGateway;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import com.vi.agent.core.runtime.memory.extract.prompt.StateDeltaExtractionPromptVariablesFactory;
import com.vi.agent.core.runtime.prompt.PromptContractTestSupport;
import com.vi.agent.core.runtime.prompt.PromptRuntimeTestSupport;
import com.vi.agent.core.runtime.prompt.StructuredLlmOutputContractGuard;
import com.vi.agent.core.runtime.support.TestFieldUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmStateDeltaExtractorTest {

    @Test
    void validDeltaFromLlmShouldReturnSuccessfulExtraction() {
        RecordingLlmGateway gateway = new RecordingLlmGateway(successChannel("""
            {
              "confirmedFactsAppend": [
                {
                  "factId": "fact-1",
                  "content": "User prefers concise checklists."
                }
              ],
              "sourceCandidateIds": ["msg-user-1"]
            }
            """));
        LlmStateDeltaExtractor extractor = extractor(gateway);

        StateDeltaExtractionResult result = extractor.extract(command());

        assertTrue(result.isSuccess());
        assertFalse(result.isDegraded());
        assertNotNull(result.getStateDelta());
        assertFalse(result.getStateDelta().isEmpty());
        assertEquals(List.of("msg-user-1"), result.getSourceCandidateIds());
        assertEquals(1, gateway.generateCalls);
        assertNotNull(gateway.lastRequest);
        assertEquals("run-1", gateway.lastRequest.getRunId());
        assertEquals(2, gateway.lastRequest.getMessages().size());
        assertEquals("itaskmsg-system-fixed", gateway.lastRequest.getMessages().get(0).getMessageId());
        assertEquals("itaskmsg-user-fixed", gateway.lastRequest.getMessages().get(1).getMessageId());
        assertEquals(MessageRole.SYSTEM, gateway.lastRequest.getMessages().get(0).getRole());
        assertEquals(MessageRole.USER, gateway.lastRequest.getMessages().get(1).getRole());
        assertTrue(gateway.lastRequest.getMessages().get(1).getContentText().contains("[BEGIN_UNTRUSTED_CURRENT_TURN_MESSAGES]"));
        assertEquals(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT, gateway.lastRequest.getStructuredOutputContract().getStructuredOutputContractKey());
        assertEquals("emit_state_delta", gateway.lastRequest.getStructuredOutputFunctionName());
        assertEquals(PromptRuntimeTestSupport.CATALOG_REVISION, result.getPromptRenderMetadata().getCatalogRevision());
    }

    @Test
    void emptyDeltaFromLlmShouldReturnSuccessfulEmptyExtraction() {
        LlmStateDeltaExtractor extractor = extractor(new RecordingLlmGateway(successChannel("{\"sourceCandidateIds\":[]}")));

        StateDeltaExtractionResult result = extractor.extract(command());

        assertTrue(result.isSuccess());
        assertFalse(result.isDegraded());
        assertTrue(result.getStateDelta().isEmpty());
    }

    @Test
    void structuredChannelFailureShouldReturnDegradedWithoutRawContentFallback() {
        RecordingLlmGateway gateway = new RecordingLlmGateway(StructuredOutputChannelResult.builder()
            .success(false)
            .actualStructuredOutputMode(StructuredLlmOutputMode.STRICT_TOOL_CALL)
            .retryCount(0)
            .failureReason("missing tool call")
            .build());
        gateway.content = """
            {"confirmedFactsAppend":[{"factId":"fact-1","content":"should not parse raw content"}]}
            """;
        LlmStateDeltaExtractor extractor = extractor(gateway);

        StateDeltaExtractionResult result = extractor.extract(command());

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("missing tool call"));
        assertNotNull(result.getStructuredOutputChannelResult());
    }

    @Test
    void llmFailureShouldReturnDegradedResult() {
        RecordingLlmGateway gateway = new RecordingLlmGateway(null);
        gateway.throwOnGenerate = true;
        LlmStateDeltaExtractor extractor = extractor(gateway);

        StateDeltaExtractionResult result = extractor.extract(command());

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("state delta extraction LLM call failed"));
    }

    private LlmStateDeltaExtractor extractor(LlmGateway gateway) {
        return new LlmStateDeltaExtractor(
            gateway,
            PromptRuntimeTestSupport.promptRenderer(),
            PromptRuntimeTestSupport.systemPromptRegistry(),
            new StateDeltaExtractionPromptVariablesFactory(),
            new StateDeltaExtractionOutputParser(
                PromptContractTestSupport.stateDeltaContract(),
                new StructuredLlmOutputContractGuard()
            ),
            new FixedInternalTaskMessageIdGenerator()
        );
    }

    private StateDeltaExtractionCommand command() {
        return StateDeltaExtractionCommand.builder()
            .conversationId("conv-1")
            .sessionId("sess-1")
            .turnId("turn-1")
            .runId("run-1")
            .traceId("trace-1")
            .agentMode(AgentMode.GENERAL)
            .turnMessages(List.of(UserMessage.create("msg-user-1", "conv-1", "sess-1", "turn-1", "run-1", 1L, "Remember this.")))
            .workingContextSnapshotId("wctx-1")
            .build();
    }

    private static final class FixedInternalTaskMessageIdGenerator extends InternalTaskMessageIdGenerator {

        @Override
        public String nextId(String role) {
            return "itaskmsg-" + role + "-fixed";
        }
    }

    private static final class RecordingLlmGateway implements LlmGateway {
        private String content;
        private final StructuredOutputChannelResult channelResult;
        private int generateCalls;
        private boolean throwOnGenerate;
        private ModelRequest lastRequest;

        private RecordingLlmGateway(StructuredOutputChannelResult channelResult) {
            this.channelResult = channelResult;
        }

        @Override
        public ModelResponse generate(ModelRequest modelRequest) {
            generateCalls++;
            lastRequest = modelRequest;
            if (throwOnGenerate) {
                throw new IllegalStateException("llm down");
            }
            return ModelResponse.builder()
                .content(content)
                .structuredOutputChannelResult(channelResult)
                .build();
        }

        @Override
        public ModelResponse generateStreaming(ModelRequest modelRequest, Consumer<String> chunkConsumer) {
            throw new UnsupportedOperationException("state extraction does not use streaming");
        }
    }

    private static StructuredOutputChannelResult successChannel(String outputJson) {
        return StructuredOutputChannelResult.builder()
            .success(true)
            .actualStructuredOutputMode(StructuredLlmOutputMode.JSON_OBJECT)
            .retryCount(0)
            .output(NormalizedStructuredLlmOutput.builder()
                .structuredOutputContractKey(StructuredLlmOutputContractKey.STATE_DELTA_OUTPUT)
                .actualStructuredOutputMode(StructuredLlmOutputMode.JSON_OBJECT)
                .outputJson(outputJson)
                .providerName("fake-provider")
                .modelName("fake-model")
                .build())
            .build();
    }
}

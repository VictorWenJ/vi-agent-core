package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.common.id.InternalTaskMessageIdGenerator;
import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.llm.ModelRequest;
import com.vi.agent.core.model.llm.ModelResponse;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.LlmGateway;
import com.vi.agent.core.runtime.prompt.PromptContractTestSupport;
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
        RecordingLlmGateway gateway = new RecordingLlmGateway("""
            {
              "confirmedFactsAppend": [
                {
                  "factId": "fact-1",
                  "content": "User prefers concise checklists."
                }
              ],
              "sourceCandidateIds": ["msg-user-1"]
            }
            """);
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
    }

    @Test
    void emptyDeltaFromLlmShouldReturnSuccessfulEmptyExtraction() {
        LlmStateDeltaExtractor extractor = extractor(new RecordingLlmGateway("{\"sourceCandidateIds\":[]}"));

        StateDeltaExtractionResult result = extractor.extract(command());

        assertTrue(result.isSuccess());
        assertFalse(result.isDegraded());
        assertTrue(result.getStateDelta().isEmpty());
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
        LlmStateDeltaExtractor extractor = new LlmStateDeltaExtractor();
        TestFieldUtils.setField(extractor, "llmGateway", gateway);
        TestFieldUtils.setField(extractor, "promptBuilder", new StateDeltaExtractionPromptBuilder());
        TestFieldUtils.setField(extractor, "outputParser", new StateDeltaExtractionOutputParser(
            PromptContractTestSupport.stateDeltaContract(),
            new StructuredLlmOutputContractGuard()
        ));
        TestFieldUtils.setField(extractor, "internalTaskMessageIdGenerator", new FixedInternalTaskMessageIdGenerator());
        return extractor;
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
        private final String content;
        private int generateCalls;
        private boolean throwOnGenerate;
        private ModelRequest lastRequest;

        private RecordingLlmGateway(String content) {
            this.content = content;
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
                .build();
        }

        @Override
        public ModelResponse generateStreaming(ModelRequest modelRequest, Consumer<String> chunkConsumer) {
            throw new UnsupportedOperationException("state extraction does not use streaming");
        }
    }
}

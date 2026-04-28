package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.common.id.InternalTaskMessageIdGenerator;
import com.vi.agent.core.model.context.AgentMode;
import com.vi.agent.core.model.llm.NormalizedStructuredLlmOutput;
import com.vi.agent.core.model.llm.ModelRequest;
import com.vi.agent.core.model.llm.ModelResponse;
import com.vi.agent.core.model.llm.StructuredOutputChannelResult;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.UserMessage;
import com.vi.agent.core.model.port.LlmGateway;
import com.vi.agent.core.model.prompt.StructuredLlmOutputContractKey;
import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import com.vi.agent.core.runtime.memory.extract.prompt.ConversationSummaryExtractionPromptVariablesFactory;
import com.vi.agent.core.runtime.prompt.PromptContractTestSupport;
import com.vi.agent.core.runtime.prompt.PromptRuntimeTestSupport;
import com.vi.agent.core.runtime.prompt.StructuredLlmOutputContractGuard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmConversationSummaryExtractorTest {

    @Test
    void extractShouldReturnSummaryWhenGatewayReturnsValidJson() {
        FakeLlmGateway gateway = new FakeLlmGateway(successChannel("{\"summaryText\":\"summary from llm\"}"));
        LlmConversationSummaryExtractor extractor = newExtractor(gateway);

        ConversationSummaryExtractionResult result = extractor.extract(command());

        assertTrue(result.isSuccess());
        assertFalse(result.isDegraded());
        assertEquals("summary from llm", result.getConversationSummary().getSummaryText());
        assertEquals("fake-provider", result.getGeneratorProvider());
        assertEquals("fake-model", result.getGeneratorModel());
        assertNotNull(gateway.lastRequest);
        assertEquals(2, gateway.lastRequest.getMessages().size());
        assertEquals(MessageRole.SYSTEM, gateway.lastRequest.getMessages().get(0).getRole());
        assertEquals(MessageRole.USER, gateway.lastRequest.getMessages().get(1).getRole());
        assertEquals(StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT, gateway.lastRequest.getStructuredOutputContract().getStructuredOutputContractKey());
        assertEquals("emit_conversation_summary", gateway.lastRequest.getStructuredOutputFunctionName());
        assertEquals(PromptRuntimeTestSupport.CATALOG_REVISION, result.getPromptRenderMetadata().getCatalogRevision());
        assertFalse(gateway.lastRequest.getMessages().stream().anyMatch(message -> message.getContentText().contains("ChatResponse")));
    }

    @Test
    void extractShouldReturnSkippedWhenGatewayReturnsSkippedJson() {
        FakeLlmGateway gateway = new FakeLlmGateway(successChannel("{\"skipped\":true,\"reason\":\"no update\"}"));
        LlmConversationSummaryExtractor extractor = newExtractor(gateway);

        ConversationSummaryExtractionResult result = extractor.extract(command());

        assertTrue(result.isSuccess());
        assertTrue(result.isSkipped());
        assertFalse(result.isDegraded());
    }

    @Test
    void extractShouldReturnDegradedWhenGatewayReturnsInvalidJson() {
        FakeLlmGateway gateway = new FakeLlmGateway(successChannel("{not json"));
        LlmConversationSummaryExtractor extractor = newExtractor(gateway);

        ConversationSummaryExtractionResult result = extractor.extract(command());

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
    }

    @Test
    void structuredChannelFailureShouldReturnDegradedWithoutRawContentFallback() {
        FakeLlmGateway gateway = new FakeLlmGateway(StructuredOutputChannelResult.builder()
            .success(false)
            .actualStructuredOutputMode(StructuredLlmOutputMode.STRICT_TOOL_CALL)
            .retryCount(0)
            .failureReason("missing structured output")
            .build());
        gateway.content = "{\"summaryText\":\"should not parse raw content\"}";
        LlmConversationSummaryExtractor extractor = newExtractor(gateway);

        ConversationSummaryExtractionResult result = extractor.extract(command());

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("missing structured output"));
        assertNotNull(result.getStructuredOutputChannelResult());
    }

    @Test
    void extractShouldReturnDegradedWhenGatewayThrows() {
        FakeLlmGateway gateway = new FakeLlmGateway(null);
        gateway.throwOnGenerate = true;
        LlmConversationSummaryExtractor extractor = newExtractor(gateway);

        ConversationSummaryExtractionResult result = extractor.extract(command());

        assertFalse(result.isSuccess());
        assertTrue(result.isDegraded());
        assertTrue(result.getFailureReason().contains("summary extraction llm call failed"));
    }

    private LlmConversationSummaryExtractor newExtractor(FakeLlmGateway gateway) {
        return new LlmConversationSummaryExtractor(
            gateway,
            PromptRuntimeTestSupport.promptRenderer(),
            PromptRuntimeTestSupport.systemPromptRegistry(),
            new ConversationSummaryExtractionPromptVariablesFactory(),
            new ConversationSummaryExtractionOutputParser(
                PromptContractTestSupport.conversationSummaryContract(),
                new StructuredLlmOutputContractGuard()
            ),
            new FixedInternalTaskMessageIdGenerator()
        );
    }

    private ConversationSummaryExtractionCommand command() {
        List<Message> turnMessages = List.of(
            UserMessage.create("msg-user", "conversation-1", "session-1", "turn-1", "run-1", 1L, "user message"),
            AssistantMessage.create("msg-assistant", "conversation-1", "session-1", "turn-1", "run-1", 2L, "assistant message", List.of(), null, null)
        );
        return ConversationSummaryExtractionCommand.builder()
            .conversationId("conversation-1")
            .sessionId("session-1")
            .turnId("turn-1")
            .runId("run-1")
            .traceId("trace-1")
            .agentMode(AgentMode.GENERAL)
            .turnMessages(turnMessages)
            .workingContextSnapshotId("wctx-1")
            .build();
    }

    private static final class FakeLlmGateway implements LlmGateway {
        private String content;
        private final StructuredOutputChannelResult channelResult;
        private boolean throwOnGenerate;
        private ModelRequest lastRequest;

        private FakeLlmGateway(StructuredOutputChannelResult channelResult) {
            this.channelResult = channelResult;
        }

        @Override
        public ModelResponse generate(ModelRequest request) {
            this.lastRequest = request;
            if (throwOnGenerate) {
                throw new IllegalStateException("boom");
            }
            return ModelResponse.builder()
                .provider("fake-provider")
                .model("fake-model")
                .content(content)
                .structuredOutputChannelResult(channelResult)
                .build();
        }

        @Override
        public ModelResponse generateStreaming(ModelRequest modelRequest, Consumer<String> chunkConsumer) {
            throw new UnsupportedOperationException("summary extraction does not use streaming");
        }
    }

    private static StructuredOutputChannelResult successChannel(String outputJson) {
        return StructuredOutputChannelResult.builder()
            .success(true)
            .actualStructuredOutputMode(StructuredLlmOutputMode.JSON_OBJECT)
            .retryCount(0)
            .output(NormalizedStructuredLlmOutput.builder()
                .structuredOutputContractKey(StructuredLlmOutputContractKey.CONVERSATION_SUMMARY_OUTPUT)
                .actualStructuredOutputMode(StructuredLlmOutputMode.JSON_OBJECT)
                .outputJson(outputJson)
                .providerName("fake-provider")
                .modelName("fake-model")
                .build())
            .build();
    }

    private static final class FixedInternalTaskMessageIdGenerator extends InternalTaskMessageIdGenerator {
        private final AtomicInteger next = new AtomicInteger();

        @Override
        public String nextId(String purpose) {
            return "internal-msg-" + purpose + "-" + next.incrementAndGet();
        }
    }
}

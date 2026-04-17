package com.vi.agent.core.app.application;

import com.vi.agent.core.app.api.dto.request.ChatRequest;
import com.vi.agent.core.app.api.dto.response.ChatStreamEvent;
import com.vi.agent.core.runtime.event.RuntimeEvent;
import com.vi.agent.core.runtime.event.RuntimeEventType;
import com.vi.agent.core.runtime.orchestrator.RuntimeOrchestrator;
import com.vi.agent.core.runtime.result.AgentExecutionResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatStreamApplicationServiceTest {

    @Test
    void streamShouldAdaptRuntimeTokenEventsToSse() {
        RuntimeOrchestrator runtimeOrchestrator = mock(RuntimeOrchestrator.class);
        when(runtimeOrchestrator.executeStreaming(eq("session-1"), eq("hello"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<RuntimeEvent> consumer = invocation.getArgument(2);
            consumer.accept(RuntimeEvent.builder()
                .type(RuntimeEventType.TOKEN)
                .traceId("trace-1")
                .runId("run-1")
                .conversationId("conversation-1")
                .turnId("turn-1")
                .content("Hel")
                .done(false)
                .build());
            consumer.accept(RuntimeEvent.builder()
                .type(RuntimeEventType.COMPLETE)
                .traceId("trace-1")
                .runId("run-1")
                .conversationId("conversation-1")
                .turnId("turn-1")
                .content("Hello")
                .done(true)
                .build());
            return AgentExecutionResult.builder()
                .traceId("trace-1")
                .runId("run-1")
                .sessionId("session-1")
                .conversationId("conversation-1")
                .turnId("turn-1")
                .build();
        });

        ChatStreamApplicationService service = new ChatStreamApplicationService(runtimeOrchestrator);
        ChatRequest request = new ChatRequest();
        request.setSessionId("session-1");
        request.setMessage("hello");

        Flux<ServerSentEvent<ChatStreamEvent>> flux = service.stream(request);

        StepVerifier.create(flux)
            .assertNext(event -> {
                assertEquals("token", event.event());
                assertEquals("Hel", event.data().getContent());
                assertEquals("turn-1", event.data().getTurnId());
            })
            .assertNext(event -> {
                assertEquals("complete", event.event());
                assertEquals("Hello", event.data().getContent());
                assertTrue(event.data().isDone());
            })
            .verifyComplete();
    }
}

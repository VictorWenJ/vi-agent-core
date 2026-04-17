package com.vi.agent.core.app.application;

import com.vi.agent.core.app.api.dto.request.ChatRequest;
import com.vi.agent.core.app.api.dto.response.ChatResponse;
import com.vi.agent.core.model.message.AssistantMessage;
import com.vi.agent.core.runtime.orchestrator.RuntimeOrchestrator;
import com.vi.agent.core.runtime.result.AgentExecutionResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatApplicationServiceTest {

    @Test
    void chatShouldRunBlockingRuntimeOnBoundedElastic() {
        RuntimeOrchestrator runtimeOrchestrator = mock(RuntimeOrchestrator.class);
        AtomicReference<String> runtimeThread = new AtomicReference<>();
        when(runtimeOrchestrator.execute(eq("session-1"), eq("hello"))).thenAnswer(invocation -> {
            runtimeThread.set(Thread.currentThread().getName());
            return AgentExecutionResult.builder()
                .traceId("trace-1")
                .runId("run-1")
                .sessionId("session-1")
                .conversationId("conversation-1")
                .turnId("turn-1")
                .assistantMessage(AssistantMessage.create("msg-1", "turn-1", "hi", List.of()))
                .build();
        });

        ChatApplicationService service = new ChatApplicationService(runtimeOrchestrator);
        ChatRequest request = new ChatRequest();
        request.setSessionId("session-1");
        request.setMessage("hello");

        ChatResponse response = service.chat(request).block(Duration.ofSeconds(3));

        assertNotNull(response);
        assertEquals("hi", response.getContent());
        assertNotNull(runtimeThread.get());
        assertTrue(runtimeThread.get().contains("boundedElastic"));
    }
}

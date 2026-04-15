package com.vi.agent.core.app.service;

import com.vi.agent.core.app.controller.dto.ChatRequest;
import com.vi.agent.core.app.controller.dto.ChatResponseChunk;
import com.vi.agent.core.runtime.orchestrator.RuntimeExecutionResult;
import com.vi.agent.core.runtime.orchestrator.RuntimeOrchestrator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 流式聊天 Facade。
 */
@Service
@RequiredArgsConstructor
public class StreamingChatService {

    /** Runtime 核心编排器。 */
    private final RuntimeOrchestrator runtimeOrchestrator;

    public Flux<ServerSentEvent<ChatResponseChunk>> stream(ChatRequest request) {
        RuntimeExecutionResult result = runtimeOrchestrator.execute(request.getSessionId(), request.getMessage());
        ChatResponseChunk chunk = new ChatResponseChunk(
            result.getTraceId(),
            result.getRunId(),
            result.getAssistantMessage().getContent(),
            true
        );
        return Flux.just(ServerSentEvent.builder(chunk).event("message").build());
    }
}

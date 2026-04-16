package com.vi.agent.core.app.service;

import com.vi.agent.core.app.controller.dto.ChatRequest;
import com.vi.agent.core.app.controller.dto.ChatResponse;
import com.vi.agent.core.runtime.orchestrator.RuntimeExecutionResult;
import com.vi.agent.core.runtime.orchestrator.RuntimeOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 同步聊天 Facade。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** Runtime 核心编排器。 */
    private final RuntimeOrchestrator runtimeOrchestrator;

    public Mono<ChatResponse> chat(ChatRequest request) {
        return Mono.fromSupplier(() -> {
            RuntimeExecutionResult result = runtimeOrchestrator.execute(request.getSessionId(), request.getMessage());
            log.info("ChatService runtime done sessionId={} runId={}", request.getSessionId(), result.getRunId());
            return ChatResponse.builder()
                .traceId(result.getTraceId())
                .runId(result.getRunId())
                .conversationId(result.getConversationId())
                .turnId(result.getTurnId())
                .content(result.getAssistantMessage().getContent())
                .build();
        });
    }
}

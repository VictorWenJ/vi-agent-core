package com.vi.agent.core.app.application;

import com.vi.agent.core.app.api.dto.request.ChatRequest;
import com.vi.agent.core.app.api.dto.response.ChatResponse;
import com.vi.agent.core.runtime.result.AgentExecutionResult;
import com.vi.agent.core.runtime.orchestrator.RuntimeOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 同步聊天 Facade。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatApplicationService {

    /** Runtime 核心编排器。 */
    private final RuntimeOrchestrator runtimeOrchestrator;

    public Mono<ChatResponse> chat(ChatRequest request) {
        return Mono.fromCallable(() -> {
            AgentExecutionResult result = runtimeOrchestrator.execute(request.getSessionId(), request.getMessage());
            return ChatResponse.builder()
                .traceId(result.getTraceId())
                .runId(result.getRunId())
                .conversationId(result.getConversationId())
                .turnId(result.getTurnId())
                .content(result.getAssistantMessage().getContent())
                .build();
        }).subscribeOn(Schedulers.boundedElastic());
    }
}

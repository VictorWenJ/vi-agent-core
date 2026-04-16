package com.vi.agent.core.app.service;

import com.vi.agent.core.app.controller.dto.ChatRequest;
import com.vi.agent.core.app.controller.dto.ChatResponseChunk;
import com.vi.agent.core.runtime.orchestrator.RuntimeOrchestrator;
import com.vi.agent.core.runtime.orchestrator.RuntimeStreamEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * 流式聊天 Facade。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingChatService {

    /** Runtime 核心编排器。 */
    private final RuntimeOrchestrator runtimeOrchestrator;

    public Flux<ServerSentEvent<ChatResponseChunk>> stream(ChatRequest request) {
        return Flux.create(sink -> Schedulers.boundedElastic().schedule(() -> {
            try {
                runtimeOrchestrator.executeStreaming(request.getSessionId(), request.getMessage(), event -> {
                    if (!shouldEmit(event.getType())) {
                        return;
                    }
                    ChatResponseChunk chunk = ChatResponseChunk.builder()
                        .traceId(event.getTraceId())
                        .runId(event.getRunId())
                        .conversationId(event.getConversationId())
                        .turnId(event.getTurnId())
                        .content(event.getContent())
                        .done(event.isDone())
                        .build();
                    sink.next(ServerSentEvent.builder(chunk)
                        .event(event.getType().name().toLowerCase())
                        .build());
                });
                sink.complete();
            } catch (Exception e) {
                log.error("StreamingChatService stream failed sessionId={}", request.getSessionId(), e);
                sink.error(e);
            }
        }));
    }

    private boolean shouldEmit(RuntimeStreamEventType type) {
        return type == RuntimeStreamEventType.TOKEN
            || type == RuntimeStreamEventType.COMPLETE
            || type == RuntimeStreamEventType.ERROR;
    }
}

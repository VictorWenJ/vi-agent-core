package com.vi.agent.core.app.application;

import com.vi.agent.core.app.api.dto.request.ChatRequest;
import com.vi.agent.core.app.api.dto.response.ChatStreamEvent;
import com.vi.agent.core.runtime.orchestrator.RuntimeOrchestrator;
import com.vi.agent.core.runtime.event.RuntimeEventType;
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
public class ChatStreamApplicationService {

    /**
     * Runtime 核心编排器。
     */
    private final RuntimeOrchestrator runtimeOrchestrator;

    public Flux<ServerSentEvent<ChatStreamEvent>> stream(ChatRequest request) {
        return Flux.create(sink -> Schedulers.boundedElastic().schedule(() -> {
            try {
                runtimeOrchestrator.executeStreaming(
                    request.getConversationId(),
                    request.getSessionId(),
                    request.getRequestId(),
                    request.getMessage(),
                    event -> {
                        if (!shouldEmit(event.getType())) {
                            return;
                        }
                        ChatStreamEvent chunk = ChatStreamEvent.builder()
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
                log.error("ChatStreamApplicationService stream failed sessionId={}", request.getSessionId(), e);
                sink.error(e);
            }
        }));
    }

    private boolean shouldEmit(RuntimeEventType type) {
        return type == RuntimeEventType.START
            || type == RuntimeEventType.ITERATION
            || type == RuntimeEventType.TOKEN
            || type == RuntimeEventType.DELTA
            || type == RuntimeEventType.TOOL_CALL
            || type == RuntimeEventType.TOOL_RESULT
            || type == RuntimeEventType.COMPLETE
            || type == RuntimeEventType.ERROR;
    }
}

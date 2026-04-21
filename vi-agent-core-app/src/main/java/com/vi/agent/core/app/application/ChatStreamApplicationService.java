package com.vi.agent.core.app.application;

import com.vi.agent.core.app.api.dto.request.ChatRequest;
import com.vi.agent.core.app.api.dto.response.ChatStreamEvent;
import com.vi.agent.core.app.application.assembler.ChatStreamEventAssembler;
import com.vi.agent.core.app.application.assembler.RuntimeCommandAssembler;
import com.vi.agent.core.app.application.validator.ChatRequestValidator;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.runtime.orchestrator.RuntimeOrchestrator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * Streaming chat facade.
 */
@Slf4j
@Service
public class ChatStreamApplicationService {

    @Resource
    private RuntimeOrchestrator runtimeOrchestrator;

    @Resource
    private ChatRequestValidator chatRequestValidator;

    @Resource
    private RuntimeCommandAssembler runtimeCommandAssembler;

    @Resource
    private ChatStreamEventAssembler chatStreamEventAssembler;

    public Flux<ServerSentEvent<ChatStreamEvent>> stream(ChatRequest request) {
        log.info("ChatStreamApplicationService stream request={}", JsonUtils.toJson(request));
        return Flux.create(sink -> Schedulers.boundedElastic().schedule(() -> {
            try {
                chatRequestValidator.validate(request);
                var command = runtimeCommandAssembler.toCommand(request);
                runtimeOrchestrator.executeStreaming(command, event -> sink.next(ServerSentEvent.builder(
                    chatStreamEventAssembler.toApiEvent(event)
                ).event(event.getEventType().name().toLowerCase()).build()));
                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        }));
    }
}

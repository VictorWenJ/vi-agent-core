package com.vi.agent.core.app.application;

import com.vi.agent.core.app.api.dto.request.ChatRequest;
import com.vi.agent.core.app.api.dto.response.ChatResponse;
import com.vi.agent.core.app.application.assembler.ChatResponseAssembler;
import com.vi.agent.core.app.application.assembler.RuntimeCommandAssembler;
import com.vi.agent.core.app.application.validator.ChatRequestValidator;
import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.runtime.orchestrator.RuntimeOrchestrator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Sync chat facade.
 */
@Slf4j
@Service
public class ChatApplicationService {

    @Resource
    private RuntimeOrchestrator runtimeOrchestrator;

    @Resource
    private ChatRequestValidator chatRequestValidator;

    @Resource
    private RuntimeCommandAssembler runtimeCommandAssembler;

    @Resource
    private ChatResponseAssembler chatResponseAssembler;

    public Mono<ChatResponse> chat(ChatRequest request) {
        log.info("ChatApplicationService chat request={}", JsonUtils.toJson(request));
        return Mono.fromCallable(() -> {
            chatRequestValidator.validate(request);
            var command = runtimeCommandAssembler.toCommand(request);
            var executionResult = runtimeOrchestrator.execute(command);
            return chatResponseAssembler.toResponse(executionResult);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}

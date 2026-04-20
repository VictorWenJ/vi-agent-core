package com.vi.agent.core.app.api.controller;

import com.vi.agent.core.app.api.dto.request.ChatRequest;
import com.vi.agent.core.app.api.dto.response.ChatStreamEvent;
import com.vi.agent.core.app.application.ChatStreamApplicationService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Streaming chat controller.
 */
@RestController
@RequestMapping(path = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public class ChatStreamController {

    @Resource
    private ChatStreamApplicationService chatStreamApplicationService;

    @PostMapping
    public Flux<ServerSentEvent<ChatStreamEvent>> stream(@Valid @RequestBody ChatRequest request) {
        return chatStreamApplicationService.stream(request);
    }
}

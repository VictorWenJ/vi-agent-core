package com.vi.agent.core.app.api.controller;

import com.vi.agent.core.app.api.dto.request.ChatRequest;
import com.vi.agent.core.app.api.dto.response.ChatResponse;
import com.vi.agent.core.app.application.ChatApplicationService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Sync chat controller.
 */
@RestController
@RequestMapping(path = "/api/chat", produces = MediaType.APPLICATION_JSON_VALUE)
public class ChatController {

    @Resource
    private ChatApplicationService chatApplicationService;

    @PostMapping
    public Mono<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return chatApplicationService.chat(request);
    }
}

package com.vi.agent.core.app.controller;

import com.vi.agent.core.app.controller.dto.ChatRequest;
import com.vi.agent.core.app.controller.dto.ChatResponse;
import com.vi.agent.core.app.service.ChatService;
import com.vi.agent.core.common.util.JsonUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 同步聊天接入控制器。
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/chat", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ChatController {

    /** 聊天 Facade 服务。 */
    private final ChatService chatService;

    @PostMapping
    public Mono<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        log.info("ChatController chat received request={}", JsonUtils.toJson(request));
        return chatService.chat(request);
    }
}

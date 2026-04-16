package com.vi.agent.core.app.controller;

import com.vi.agent.core.app.controller.dto.ChatRequest;
import com.vi.agent.core.app.controller.dto.ChatResponseChunk;
import com.vi.agent.core.app.service.StreamingChatService;
import com.vi.agent.core.common.util.JsonUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 流式聊天接入控制器。
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
@RequiredArgsConstructor
public class StreamController {

    /** 流式聊天 Facade 服务。 */
    private final StreamingChatService streamingChatService;

    @PostMapping
    public Flux<ServerSentEvent<ChatResponseChunk>> stream(@Valid @RequestBody ChatRequest request) {
        log.info("StreamController stream received request={}", JsonUtils.toJson(request));
        return streamingChatService.stream(request);
    }
}

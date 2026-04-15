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
        Flux<ServerSentEvent<ChatResponseChunk>> response = null;
        try {
            response = streamingChatService.stream(request);
        } catch (Exception e) {
            log.error("StreamController stream error", e);
            throw new RuntimeException(e);
        } finally {
            log.info("StreamController stream request:{}, response:{}", JsonUtils.toJson(request), JsonUtils.toJson(response));
        }
        return response;
    }
}

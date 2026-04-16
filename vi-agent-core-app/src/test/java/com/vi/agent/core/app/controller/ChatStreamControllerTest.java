package com.vi.agent.core.app.controller;

import com.vi.agent.core.app.api.advice.GlobalExceptionHandler;
import com.vi.agent.core.app.api.controller.ChatStreamController;
import com.vi.agent.core.app.api.dto.request.ChatRequest;
import com.vi.agent.core.app.api.dto.response.ChatStreamEvent;
import com.vi.agent.core.app.api.application.ChatStreamApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@WebFluxTest(controllers = ChatStreamController.class)
@Import(GlobalExceptionHandler.class)
class ChatStreamControllerTest {

    private final WebTestClient webTestClient;

    ChatStreamControllerTest(@Autowired WebTestClient webTestClient) {
        this.webTestClient = webTestClient;
    }

    @MockBean
    private ChatStreamApplicationService chatStreamApplicationService;

    @Test
    void streamShouldReturnSseChunks() {
        ChatStreamEvent chunk = ChatStreamEvent.builder()
            .traceId("trace-1")
            .runId("run-1")
            .conversationId("conv-1")
            .turnId("turn-1")
            .content("hello")
            .done(true)
            .build();

        given(chatStreamApplicationService.stream(any(ChatRequest.class))).willReturn(
            Flux.just(ServerSentEvent.builder(chunk).event("complete").build())
        );

        FluxExchangeResult<ChatStreamEvent> result = webTestClient.post()
            .uri("/api/chat/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue("{\"sessionId\":\"s-1\",\"message\":\"hi\"}")
            .exchange()
            .expectStatus().isOk()
            .returnResult(ChatStreamEvent.class);

        StepVerifier.create(result.getResponseBody())
            .expectNextMatches(responseChunk -> "trace-1".equals(responseChunk.getTraceId())
                && "hello".equals(responseChunk.getContent()))
            .verifyComplete();
    }
}

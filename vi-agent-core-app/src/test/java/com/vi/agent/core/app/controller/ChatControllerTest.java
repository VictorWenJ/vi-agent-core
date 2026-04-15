package com.vi.agent.core.app.controller;

import com.vi.agent.core.app.controller.dto.ChatRequest;
import com.vi.agent.core.app.controller.dto.ChatResponse;
import com.vi.agent.core.app.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@WebFluxTest(controllers = ChatController.class)
class ChatControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ChatService chatService;

    @Test
    void chatShouldReturnSuccessResponse() {
        ChatResponse response = new ChatResponse("trace-1", "run-1", "hello");
        given(chatService.chat(any(ChatRequest.class))).willReturn(Mono.just(response));

        webTestClient.post()
            .uri("/api/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"sessionId\":\"s-1\",\"message\":\"hi\"}")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.traceId").isEqualTo("trace-1")
            .jsonPath("$.runId").isEqualTo("run-1")
            .jsonPath("$.content").isEqualTo("hello");
    }
}

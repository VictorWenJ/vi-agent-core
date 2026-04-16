package com.vi.agent.core.app.controller;

import com.vi.agent.core.app.advice.GlobalExceptionHandler;
import com.vi.agent.core.app.controller.dto.ChatRequest;
import com.vi.agent.core.app.controller.dto.ChatResponse;
import com.vi.agent.core.app.service.ChatService;
import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@WebFluxTest(controllers = ChatController.class)
@Import(GlobalExceptionHandler.class)
class ChatControllerTest {

    private final WebTestClient webTestClient;

    ChatControllerTest(@Autowired WebTestClient webTestClient) {
        this.webTestClient = webTestClient;
    }

    @MockBean
    private ChatService chatService;

    @Test
    void chatShouldReturnSuccessResponse() {
        ChatResponse response = ChatResponse.builder()
            .traceId("trace-1")
            .runId("run-1")
            .conversationId("conv-1")
            .turnId("turn-1")
            .content("hello")
            .build();
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
            .jsonPath("$.conversationId").isEqualTo("conv-1")
            .jsonPath("$.turnId").isEqualTo("turn-1")
            .jsonPath("$.content").isEqualTo("hello");
    }

    @Test
    void chatShouldReturnErrorResponseWhenBusinessException() {
        given(chatService.chat(any(ChatRequest.class))).willReturn(Mono.error(
            new AgentRuntimeException(ErrorCode.INVALID_ARGUMENT, "bad request")
        ));

        webTestClient.post()
            .uri("/api/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"sessionId\":\"s-1\",\"message\":\"hi\"}")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody()
            .jsonPath("$.errorCode").isEqualTo("COMMON-0001")
            .jsonPath("$.errorMessage").isEqualTo("bad request");
    }
}

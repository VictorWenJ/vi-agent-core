package com.vi.agent.core.app.api.advice;

import com.vi.agent.core.app.api.dto.response.ApiErrorResponse;
import com.vi.agent.core.common.exception.AgentRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AgentRuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleAgentRuntimeException(AgentRuntimeException ex) {
        log.info("Business error code={} msg={}", ex.getErrorCode().getCode(), ex.getMessage());
        ApiErrorResponse response = ApiErrorResponse.builder()
            .errorCode(ex.getErrorCode().getCode())
            .errorMessage(ex.getMessage())
            .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception ex) {
        log.error("Unexpected error", ex);
        ApiErrorResponse response = ApiErrorResponse.builder()
            .errorCode("COMMON-500")
            .errorMessage(ex.getMessage())
            .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}

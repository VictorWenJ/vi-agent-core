package com.vi.agent.core.app.api.advice;

import com.vi.agent.core.app.api.dto.response.ApiErrorResponse;
import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
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
        return ResponseEntity.status(mapStatus(ex.getErrorCode())).body(response);
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

    private HttpStatus mapStatus(ErrorCode errorCode) {
        if (errorCode == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return switch (errorCode) {
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case TOOL_NOT_FOUND, TOOL_NOT_REGISTERED -> HttpStatus.NOT_FOUND;
            case PROVIDER_CALL_FAILED -> HttpStatus.BAD_GATEWAY;
            case TRANSCRIPT_STORE_FAILED, RUNTIME_EXECUTION_FAILED, PROVIDER_CONFIG_INVALID_FAILED, JSON_SERIALIZATION_FAILED ->
                HttpStatus.INTERNAL_SERVER_ERROR;
            case RUNTIME_MAX_ITERATIONS_EXCEEDED -> HttpStatus.CONFLICT;
            case TOOL_EXECUTION_FAILED -> HttpStatus.BAD_REQUEST;
        };
    }
}

package com.vi.agent.core.app.api.advice;

import com.vi.agent.core.app.api.dto.response.ApiErrorResponse;
import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。
 * 负责将业务异常转换为标准 HTTP 响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String DEFAULT_ERROR_CODE = "COMMON-500";
    private static final String DEFAULT_ERROR_MESSAGE = "Internal server error";

    @ExceptionHandler(AgentRuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleAgentRuntimeException(AgentRuntimeException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        HttpStatus status = mapStatus(errorCode);

        ApiErrorResponse response = ApiErrorResponse.builder()
            .errorCode(errorCode != null ? errorCode.getCode() : DEFAULT_ERROR_CODE)
            .errorMessage(resolveMessage(ex.getMessage(), errorCode))
            .build();

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception ex) {
        ApiErrorResponse response = ApiErrorResponse.builder()
            .errorCode(DEFAULT_ERROR_CODE)
            .errorMessage(resolveMessage(ex.getMessage(), null))
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    private HttpStatus mapStatus(ErrorCode errorCode) {
        if (errorCode == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return switch (errorCode) {
            case INVALID_ARGUMENT, SESSION_MODE_INVALID -> HttpStatus.BAD_REQUEST;

            case TOOL_NOT_FOUND, TOOL_NOT_REGISTERED, CONVERSATION_NOT_FOUND, SESSION_NOT_FOUND -> HttpStatus.NOT_FOUND;

            case TOOL_EXECUTION_FAILED -> HttpStatus.UNPROCESSABLE_ENTITY;

            case RUNTIME_MAX_ITERATIONS_EXCEEDED, SESSION_CONCURRENT_REQUEST -> HttpStatus.CONFLICT;

            case SESSION_CONVERSATION_MISMATCH -> HttpStatus.BAD_REQUEST;

            case PROVIDER_CALL_FAILED -> HttpStatus.BAD_GATEWAY;

            case RUNTIME_EXECUTION_FAILED,
                 PROVIDER_CONFIG_INVALID_FAILED,
                 TRANSCRIPT_STORE_FAILED,
                 JSON_SERIALIZATION_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private String resolveMessage(String exceptionMessage, ErrorCode errorCode) {
        if (exceptionMessage != null && !exceptionMessage.isBlank()) {
            return exceptionMessage;
        }
        if (errorCode != null && errorCode.getMessage() != null && !errorCode.getMessage().isBlank()) {
            return errorCode.getMessage();
        }
        return DEFAULT_ERROR_MESSAGE;
    }
}

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
 * 负责将业务异常转换为标准 HTTP 响应。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String DEFAULT_ERROR_CODE = "COMMON-500";
    private static final String DEFAULT_ERROR_MESSAGE = "系统内部错误";

    @ExceptionHandler(AgentRuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleAgentRuntimeException(AgentRuntimeException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        HttpStatus status = mapStatus(errorCode);

        log.warn(
            "AgentRuntimeException caught errorCode={} httpStatus={} message={}",
            errorCode != null ? errorCode.getCode() : DEFAULT_ERROR_CODE,
            status.value(),
            ex.getMessage(),
            ex
        );

        ApiErrorResponse response = ApiErrorResponse.builder()
            .errorCode(errorCode != null ? errorCode.getCode() : DEFAULT_ERROR_CODE)
            .errorMessage(resolveMessage(ex.getMessage(), errorCode))
            .build();

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception ex) {
        log.error("Unexpected exception caught", ex);

        ApiErrorResponse response = ApiErrorResponse.builder()
            .errorCode(DEFAULT_ERROR_CODE)
            .errorMessage(resolveMessage(ex.getMessage(), null))
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * 根据当前项目已有 ErrorCode 映射 HTTP 状态码。
     */
    private HttpStatus mapStatus(ErrorCode errorCode) {
        if (errorCode == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        return switch (errorCode) {
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;

            case TOOL_NOT_FOUND, TOOL_NOT_REGISTERED -> HttpStatus.NOT_FOUND;

            case TOOL_EXECUTION_FAILED -> HttpStatus.UNPROCESSABLE_ENTITY;

            case RUNTIME_MAX_ITERATIONS_EXCEEDED -> HttpStatus.CONFLICT;

            case PROVIDER_CALL_FAILED -> HttpStatus.BAD_GATEWAY;

            case RUNTIME_EXECUTION_FAILED,
                 PROVIDER_CONFIG_INVALID_FAILED,
                 TRANSCRIPT_STORE_FAILED,
                 JSON_SERIALIZATION_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * 优先返回异常消息；没有消息时回退到 ErrorCode 默认消息；再没有则给统一兜底文案。
     */
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
package com.vi.agent.core.app.advice;

import com.vi.agent.core.app.controller.dto.ApiErrorResponse;
import com.vi.agent.core.common.exception.AgentRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AgentRuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleAgentRuntimeException(AgentRuntimeException ex) {
        ApiErrorResponse response = new ApiErrorResponse(
            ex.getErrorCode().getCode(),
            ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception ex) {
        ApiErrorResponse response = new ApiErrorResponse("COMMON-500", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}

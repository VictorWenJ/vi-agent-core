package com.vi.agent.core.common.exception;

import lombok.Getter;

/**
 * Agent 运行时统一业务异常。
 */
@Getter
public class AgentRuntimeException extends RuntimeException {

    /** 错误码。 */
    private final ErrorCode errorCode;

    public AgentRuntimeException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AgentRuntimeException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}

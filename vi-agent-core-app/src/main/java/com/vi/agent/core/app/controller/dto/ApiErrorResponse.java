package com.vi.agent.core.app.controller.dto;

/**
 * 错误响应 DTO。
 */
public class ApiErrorResponse {

    /** 错误码。 */
    private String errorCode;

    /** 错误信息。 */
    private String errorMessage;

    public ApiErrorResponse() {
    }

    public ApiErrorResponse(String errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}

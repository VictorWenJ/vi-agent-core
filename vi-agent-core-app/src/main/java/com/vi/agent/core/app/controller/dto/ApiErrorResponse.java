package com.vi.agent.core.app.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 错误响应 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {

    /** 错误码。 */
    private String errorCode;

    /** 错误信息。 */
    private String errorMessage;

}

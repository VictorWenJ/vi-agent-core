package com.vi.agent.core.app.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 错误响应 DTO。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {

    /** 错误码。 */
    private String errorCode;

    /** 错误信息。 */
    private String errorMessage;
}

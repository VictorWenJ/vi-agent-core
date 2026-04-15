package com.vi.agent.core.app.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天响应 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** 链路追踪 ID。 */
    private String traceId;

    /** 运行 ID。 */
    private String runId;

    /** 助手输出内容。 */
    private String content;

}

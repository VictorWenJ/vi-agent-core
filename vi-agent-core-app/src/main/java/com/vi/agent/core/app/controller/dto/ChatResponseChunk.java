package com.vi.agent.core.app.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式响应分片 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseChunk {

    /** 链路追踪 ID。 */
    private String traceId;

    /** 运行 ID。 */
    private String runId;

    /** 当前分片内容。 */
    private String content;

    /** 是否结束。 */
    private boolean done;

}

package com.vi.agent.core.app.controller.dto;

/**
 * 聊天响应 DTO。
 */
public class ChatResponse {

    /** 链路追踪 ID。 */
    private String traceId;

    /** 运行 ID。 */
    private String runId;

    /** 助手输出内容。 */
    private String content;

    public ChatResponse() {
    }

    public ChatResponse(String traceId, String runId, String content) {
        this.traceId = traceId;
        this.runId = runId;
        this.content = content;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

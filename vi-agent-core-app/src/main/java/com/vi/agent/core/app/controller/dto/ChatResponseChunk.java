package com.vi.agent.core.app.controller.dto;

/**
 * 流式响应分片 DTO。
 */
public class ChatResponseChunk {

    /** 链路追踪 ID。 */
    private String traceId;

    /** 运行 ID。 */
    private String runId;

    /** 当前分片内容。 */
    private String content;

    /** 是否结束。 */
    private boolean done;

    public ChatResponseChunk() {
    }

    public ChatResponseChunk(String traceId, String runId, String content, boolean done) {
        this.traceId = traceId;
        this.runId = runId;
        this.content = content;
        this.done = done;
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

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }
}

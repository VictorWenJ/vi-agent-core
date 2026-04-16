package com.vi.agent.core.infra.persistence.entity;

import lombok.*;

import java.time.Instant;

/**
 * Transcript 持久化实体（Redis Hash 映射对象）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisTranscriptEntity {

    /**
     * 会话 ID。
     */
    private String sessionId;

    /**
     * 会话链路 ID。
     */
    private String conversationId;

    /**
     * 链路追踪 ID。
     */
    private String traceId;

    /**
     * 运行 ID。
     */
    private String runId;

    /**
     * 消息列表 JSON。
     */
    private String messagesJson;

    /**
     * 工具调用列表 JSON。
     */
    private String toolCallsJson;

    /**
     * 工具结果列表 JSON。
     */
    private String toolResultsJson;

    /**
     * 更新时间。
     */
    private Instant updatedAt;
}

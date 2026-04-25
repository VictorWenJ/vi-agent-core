package com.vi.agent.core.model.context;

import com.vi.agent.core.model.message.Message;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.time.Instant;
import java.util.List;

/**
 * provider-ready 的上下文投影结果。
 */
@Getter
@Builder
public class WorkingContextProjection {

    /** provider-ready 投影 ID。 */
    private final String projectionId;

    /** 来源 WorkingContext 快照 ID。 */
    private final String workingContextSnapshotId;

    /** 投影后的模型消息列表。 */
    @Singular("message")
    private final List<Message> messages;

    /** 预计输入 token 数。 */
    private final Integer estimatedInputTokens;

    /** 投影创建时间。 */
    private final Instant createdAt;
}

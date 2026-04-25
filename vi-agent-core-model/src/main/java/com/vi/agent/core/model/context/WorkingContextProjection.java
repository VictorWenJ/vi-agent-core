package com.vi.agent.core.model.context;

import com.vi.agent.core.model.message.Message;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

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

    /** 当前 projection 的视图类型。 */
    private final ContextViewType contextViewType;

    /** 投影后的模型消息列表。 */
    @Singular("modelMessage")
    private final List<Message> modelMessages;

    /** 预计输入 token 数。 */
    private final Integer inputTokenEstimate;
}

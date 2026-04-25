package com.vi.agent.core.model.memory;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;

/**
 * 当前未闭环事项。
 */
@Getter
@Builder
@Jacksonized
public class OpenLoop {

    /** loop ID。 */
    private final String loopId;

    /** 未完成事项类型。 */
    private final OpenLoopKind kind;

    /** 未完成事项内容。 */
    private final String content;

    /** 当前状态。 */
    private final OpenLoopStatus status;

    /** 来源类型，如 USER / TOOL / SYSTEM。 */
    private final String sourceType;

    /** 来源引用，如 messageId / toolCallRecordId。 */
    private final String sourceRef;

    /** 创建时间。 */
    private final Instant createdAt;

    /** 关闭时间，可空。 */
    private final Instant closedAt;
}

package com.vi.agent.core.model.context.block;

import lombok.Builder;
import lombok.Getter;

/**
 * Context block 的来源引用。
 */
@Getter
@Builder
public class ContextSourceRef {

    /** 来源类型。 */
    private final String sourceType;

    /** 来源对象 ID。 */
    private final String sourceId;

    /** 来源对象字段路径。 */
    private final String fieldPath;
}

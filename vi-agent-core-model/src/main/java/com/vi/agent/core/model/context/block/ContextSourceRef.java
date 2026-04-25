package com.vi.agent.core.model.context.block;

import com.vi.agent.core.model.context.ContextSourceType;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * Context block 的来源引用。
 */
@Getter
@Builder
@Jacksonized
public class ContextSourceRef {

    /** 来源类型。 */
    private final ContextSourceType sourceType;

    /** 来源对象 ID。 */
    private final String sourceId;

    /** 来源对象版本。 */
    private final String sourceVersion;

    /** 来源字段路径。 */
    private final String fieldPath;
}

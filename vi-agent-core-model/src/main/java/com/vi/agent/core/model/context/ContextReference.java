package com.vi.agent.core.model.context;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * 轻量上下文引用对象。
 */
@Getter
@Builder
@Jacksonized
public class ContextReference {

    /** 上下文引用 ID。 */
    private final String referenceId;

    /** 上下文引用类型。 */
    private final String referenceType;

    /** 给模型可见的轻量展示文本。 */
    private final String displayText;

    /** 被引用实体的稳定 ID。 */
    private final String targetRef;

    /** 按需加载提示。 */
    private final String loadHint;
}
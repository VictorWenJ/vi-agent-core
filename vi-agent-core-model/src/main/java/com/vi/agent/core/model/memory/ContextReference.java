package com.vi.agent.core.model.memory;

import com.vi.agent.core.model.context.ContextPriority;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * 轻量上下文引用对象。
 */
@Getter
@Builder
public class ContextReference {

    /** 上下文引用 ID。 */
    private final String referenceId;

    /** 上下文引用类型。 */
    private final String referenceType;

    /** 引用目标 ID。 */
    private final String targetId;

    /** 引用标题。 */
    private final String title;

    /** 引用描述。 */
    private final String description;

    /** 引用装配优先级。 */
    private final ContextPriority priority;

    /** 支撑该上下文引用的 evidence ID 列表。 */
    @Singular("evidenceId")
    private final List<String> evidenceIds;
}

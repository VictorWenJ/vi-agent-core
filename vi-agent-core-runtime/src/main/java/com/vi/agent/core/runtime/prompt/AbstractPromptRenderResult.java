package com.vi.agent.core.runtime.prompt;

import com.vi.agent.core.model.prompt.PromptPurpose;
import com.vi.agent.core.model.prompt.PromptRenderMetadata;
import com.vi.agent.core.model.prompt.PromptRenderOutputType;
import com.vi.agent.core.model.prompt.SystemPromptKey;
import lombok.Getter;

/**
 * prompt 渲染结构化结果抽象基类。
 */
@Getter
public abstract class AbstractPromptRenderResult implements PromptRenderResult {

    /** 系统 prompt key。 */
    private final SystemPromptKey promptKey;

    /** prompt 用途。 */
    private final PromptPurpose purpose;

    /** 渲染输出形态。 */
    private final PromptRenderOutputType renderOutputType;

    /** 渲染元数据。 */
    private final PromptRenderMetadata metadata;

    /**
     * 构造 prompt 渲染结构化结果。
     */
    protected AbstractPromptRenderResult(
        SystemPromptKey promptKey,
        PromptPurpose purpose,
        PromptRenderOutputType renderOutputType,
        PromptRenderMetadata metadata
    ) {
        this.promptKey = promptKey;
        this.purpose = purpose;
        this.renderOutputType = renderOutputType;
        this.metadata = metadata;
    }
}

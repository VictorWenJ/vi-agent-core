package com.vi.agent.core.runtime.prompt;

import com.vi.agent.core.model.prompt.PromptRenderMetadata;
import com.vi.agent.core.model.prompt.PromptRenderOutputType;
import lombok.Getter;

/**
 * prompt 渲染结构化结果抽象基类。
 */
@Getter
public abstract class AbstractPromptRenderResult implements PromptRenderResult {

    /** 渲染输出形态。 */
    private final PromptRenderOutputType renderOutputType;

    /** 渲染元数据。 */
    private final PromptRenderMetadata metadata;

    /**
     * 构造 prompt 渲染结构化结果。
     */
    protected AbstractPromptRenderResult(
        PromptRenderOutputType renderOutputType,
        PromptRenderMetadata metadata
    ) {
        this.renderOutputType = renderOutputType;
        this.metadata = metadata;
    }

    @Override
    public PromptRenderOutputType renderOutputType() {
        return renderOutputType;
    }

    @Override
    public PromptRenderMetadata metadata() {
        return metadata;
    }
}

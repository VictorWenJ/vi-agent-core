package com.vi.agent.core.runtime.prompt;

import com.vi.agent.core.model.prompt.PromptRenderMetadata;
import com.vi.agent.core.model.prompt.PromptRenderOutputType;
import lombok.Getter;

/**
 * TEXT prompt 渲染结果。
 */
@Getter
public final class TextPromptRenderResult extends AbstractPromptRenderResult {

    /** 渲染后的文本。 */
    private final String renderedText;

    /**
     * 构造 TEXT prompt 渲染结果。
     */
    public TextPromptRenderResult(PromptRenderMetadata metadata, String renderedText) {
        super(PromptRenderOutputType.TEXT, metadata);
        this.renderedText = renderedText;
    }
}

package com.vi.agent.core.runtime.prompt;

import com.vi.agent.core.model.prompt.PromptRenderMetadata;
import com.vi.agent.core.model.prompt.PromptRenderOutputType;

/**
 * prompt 渲染结构化结果。
 */
public interface PromptRenderResult {

    /**
     * 返回渲染输出形态。
     */
    PromptRenderOutputType renderOutputType();

    /**
     * 返回渲染元数据。
     */
    PromptRenderMetadata metadata();
}

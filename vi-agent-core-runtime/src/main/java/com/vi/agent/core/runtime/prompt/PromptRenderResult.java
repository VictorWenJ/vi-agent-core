package com.vi.agent.core.runtime.prompt;

import com.vi.agent.core.model.prompt.PromptPurpose;
import com.vi.agent.core.model.prompt.PromptRenderMetadata;
import com.vi.agent.core.model.prompt.PromptRenderOutputType;
import com.vi.agent.core.model.prompt.SystemPromptKey;

/**
 * prompt 渲染结构化结果。
 */
public interface PromptRenderResult {

    /**
     * 返回系统 prompt key。
     */
    SystemPromptKey getPromptKey();

    /**
     * 返回 prompt 用途。
     */
    PromptPurpose getPurpose();

    /**
     * 返回渲染输出形态。
     */
    PromptRenderOutputType getRenderOutputType();

    /**
     * 返回渲染元数据。
     */
    PromptRenderMetadata getMetadata();
}

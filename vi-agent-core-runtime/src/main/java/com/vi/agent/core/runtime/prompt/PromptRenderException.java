package com.vi.agent.core.runtime.prompt;

/**
 * prompt 渲染异常。
 */
public class PromptRenderException extends RuntimeException {

    /**
     * 构造 prompt 渲染异常。
     */
    public PromptRenderException(String message) {
        super(message);
    }

    /**
     * 构造带原因的 prompt 渲染异常。
     */
    public PromptRenderException(String message, Throwable cause) {
        super(message, cause);
    }
}

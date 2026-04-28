package com.vi.agent.core.model.prompt;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * prompt 渲染输出形态。
 */
@Getter
@AllArgsConstructor
public enum PromptRenderOutputType {

    /** 渲染成一段文本。 */
    TEXT("text", "渲染成一段文本，用于主聊天上下文 block"),

    /** 渲染成一组模型消息。 */
    CHAT_MESSAGES("chat_messages", "渲染成一组 system / user messages，用于内部 LLM worker 调用模型");

    /** manifest 和审计中使用的稳定值。 */
    private final String value;

    /** 中文说明。 */
    private final String description;
}

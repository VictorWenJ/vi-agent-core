package com.vi.agent.core.runtime.prompt;

import com.vi.agent.core.model.message.MessageRole;
import lombok.Builder;
import lombok.Value;

/**
 * prompt 渲染后的单条模型消息。
 */
@Value
@Builder(toBuilder = true)
public class PromptRenderedMessage {

    /** 消息顺序。 */
    Integer order;

    /** 消息角色。 */
    MessageRole role;

    /** 渲染后的消息内容。 */
    String renderedContent;
}

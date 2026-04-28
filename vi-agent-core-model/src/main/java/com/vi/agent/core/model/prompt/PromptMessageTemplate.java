package com.vi.agent.core.model.prompt;

import com.vi.agent.core.model.message.MessageRole;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * CHAT_MESSAGES prompt 中的单条消息模板。
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class PromptMessageTemplate {

    /** 消息顺序。 */
    Integer order;

    /** 消息角色。 */
    MessageRole role;

    /** 消息内容模板。 */
    String contentTemplate;
}

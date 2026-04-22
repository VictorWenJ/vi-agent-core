package com.vi.agent.core.runtime.context;

import com.vi.agent.core.model.message.Message;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 模型上下文消息过滤器。
 */
@Component
public class ModelContextMessageFilter {

    public List<Message> filter(List<Message> messages) {
        if (CollectionUtils.isEmpty(messages)) {
            return List.of();
        }
        return messages.stream().filter(this::isModelContextMessage).toList();
    }

    public boolean isModelContextMessage(Message message) {
        return message != null;
    }
}

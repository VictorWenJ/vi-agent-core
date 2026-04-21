package com.vi.agent.core.runtime.context;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.ToolCallMessage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Filters messages that are legal for model context.
 */
@Component
public class ModelContextMessageFilter {

    public List<Message> filter(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        return messages.stream()
            .filter(this::isModelContextMessage)
            .toList();
    }

    public boolean isModelContextMessage(Message message) {
        if (message == null) {
            return false;
        }
        return !(message instanceof ToolCallMessage);
    }
}


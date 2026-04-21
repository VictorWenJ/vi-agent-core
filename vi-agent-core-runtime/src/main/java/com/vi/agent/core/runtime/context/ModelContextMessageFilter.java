package com.vi.agent.core.runtime.context;

import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.MessageType;
import com.vi.agent.core.model.message.ToolCallMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Filters messages that are legal for model context.
 */
@Component
public class ModelContextMessageFilter {

    public List<Message> filter(List<Message> messages) {
        return Optional.ofNullable(messages)
            .map(mess -> mess.stream()
                .filter(mes -> MessageType.TOOL_CALL != mes.getMessageType())
                .toList())
            .orElse(List.of());
    }
}


package com.vi.agent.core.runtime.context.projector;

import com.vi.agent.core.model.context.ContextBlockType;
import com.vi.agent.core.model.message.MessageRole;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * Generates identifiers for synthetic context projection messages.
 */
@Component
public class SyntheticMessageIdGenerator {

    public String newSyntheticMessageId(MessageRole role, ContextBlockType blockType) {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(blockType, "blockType must not be null");
        return "ctxmsg-" + role.getValue() + "-" + blockType.getValue() + "-" + UUID.randomUUID();
    }
}

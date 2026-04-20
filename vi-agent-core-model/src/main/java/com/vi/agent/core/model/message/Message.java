package com.vi.agent.core.model.message;

import java.time.Instant;

/**
 * Runtime message abstraction.
 */
public interface Message {

    String getMessageId();

    String getTurnId();

    MessageRole getRole();

    MessageType getMessageType();

    long getSequenceNo();

    String getContent();

    Instant getCreatedAt();
}

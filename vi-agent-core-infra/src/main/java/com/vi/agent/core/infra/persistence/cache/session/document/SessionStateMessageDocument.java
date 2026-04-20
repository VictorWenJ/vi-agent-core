package com.vi.agent.core.infra.persistence.cache.session.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Message payload stored in session state cache.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionStateMessageDocument {

    private String messageId;

    private String turnId;

    private String role;

    private String messageType;

    private long sequenceNo;

    private String content;

    private Instant createdAt;
}

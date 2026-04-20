package com.vi.agent.core.runtime.command;

import com.vi.agent.core.model.session.SessionMode;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Runtime execute command.
 */
@Getter
@Builder
public class RuntimeExecuteCommand {

    private final String requestId;

    private final String conversationId;

    private final String sessionId;

    private final SessionMode sessionMode;

    private final String message;

    private final Map<String, Object> metadata;
}

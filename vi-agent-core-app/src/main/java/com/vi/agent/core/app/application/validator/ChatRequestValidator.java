package com.vi.agent.core.app.application.validator;

import com.vi.agent.core.app.api.dto.request.ChatRequest;
import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.model.session.SessionMode;
import org.springframework.stereotype.Component;

/**
 * Validates chat request semantic constraints.
 */
@Component
public class ChatRequestValidator {

    public void validate(ChatRequest request) {
        if (request.getSessionMode() == null) {
            throw new AgentRuntimeException(ErrorCode.SESSION_MODE_INVALID, "sessionMode is required");
        }

        SessionMode mode = request.getSessionMode();
        String conversationId = request.getConversationId();
        String sessionId = request.getSessionId();

        switch (mode) {
            case NEW_CONVERSATION -> {
                if (!blank(conversationId) || !blank(sessionId)) {
                    throw new AgentRuntimeException(ErrorCode.SESSION_MODE_INVALID,
                        "NEW_CONVERSATION requires empty conversationId/sessionId");
                }
            }
            case CONTINUE_EXACT_SESSION -> {
                if (blank(conversationId) || blank(sessionId)) {
                    throw new AgentRuntimeException(ErrorCode.SESSION_MODE_INVALID,
                        "CONTINUE_EXACT_SESSION requires conversationId and sessionId");
                }
            }
            case CONTINUE_ACTIVE_SESSION, START_NEW_SESSION -> {
                if (blank(conversationId) || !blank(sessionId)) {
                    throw new AgentRuntimeException(ErrorCode.SESSION_MODE_INVALID,
                        mode + " requires conversationId and empty sessionId");
                }
            }
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

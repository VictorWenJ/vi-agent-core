package com.vi.agent.core.app.application.assembler;

import com.vi.agent.core.app.api.dto.request.ChatRequest;
import com.vi.agent.core.runtime.command.RuntimeExecuteCommand;
import org.springframework.stereotype.Component;

/**
 * Assembles runtime command from API request.
 */
@Component
public class RuntimeCommandAssembler {

    public RuntimeExecuteCommand toCommand(ChatRequest request) {
        return RuntimeExecuteCommand.builder()
            .requestId(request.getRequestId())
            .conversationId(request.getConversationId())
            .sessionId(request.getSessionId())
            .sessionMode(request.getSessionMode())
            .message(request.getMessage())
            .metadata(request.getMetadata())
            .build();
    }
}

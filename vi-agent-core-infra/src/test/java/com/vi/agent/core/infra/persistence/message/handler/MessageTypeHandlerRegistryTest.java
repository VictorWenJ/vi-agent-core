package com.vi.agent.core.infra.persistence.message.handler;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.infra.persistence.message.model.MessageAggregateRows;
import com.vi.agent.core.infra.persistence.message.model.MessageWritePlan;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.MessageType;
import com.vi.agent.core.model.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageTypeHandlerRegistryTest {

    @Test
    void shouldRegisterAndResolveHandlers() {
        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry();
        registry.registerHandlers(List.of(
            new UserMessageTypeHandler(),
            new AssistantMessageTypeHandler(),
            new ToolMessageTypeHandler(),
            new SystemMessageTypeHandler(),
            new SummaryMessageTypeHandler()
        ));

        UserMessage userMessage = UserMessage.create("msg-1", "conv-1", "sess-1", "turn-1", "run-1", 1L, "hello");
        MessageWritePlan writePlan = registry.decompose(userMessage);

        Message assembled = registry.assemble(MessageAggregateRows.builder().message(writePlan.getMessage()).build());
        assertInstanceOf(UserMessage.class, assembled);
        assertEquals("msg-1", assembled.getMessageId());
    }

    @Test
    void shouldThrowWhenNoHandlerFound() {
        MessageTypeHandlerRegistry registry = new MessageTypeHandlerRegistry();
        registry.registerHandlers(List.of(new UserMessageTypeHandler()));

        AgentRuntimeException exception = assertThrows(
            AgentRuntimeException.class,
            () -> registry.get(MessageRole.ASSISTANT, MessageType.ASSISTANT_OUTPUT)
        );
        assertEquals(ErrorCode.INVALID_ARGUMENT, exception.getErrorCode());
    }
}

package com.vi.agent.core.app.integration;

import com.vi.agent.core.app.ViAgentCoreApplication;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentMessageEntity;
import com.vi.agent.core.infra.persistence.mysql.entity.AgentTurnEntity;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentMessageMapper;
import com.vi.agent.core.infra.persistence.mysql.mapper.AgentTurnMapper;
import com.vi.agent.core.model.message.Message;
import com.vi.agent.core.model.message.MessageRole;
import com.vi.agent.core.model.message.MessageStatus;
import com.vi.agent.core.model.message.MessageType;
import com.vi.agent.core.model.port.MessageRepository;
import com.vi.agent.core.model.turn.TurnStatus;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = ViAgentCoreApplication.class)
@ActiveProfiles("test")
class MessageRepositoryIntegrationTest {

    @Resource
    private MessageRepository messageRepository;

    @Resource
    private AgentTurnMapper turnMapper;

    @Resource
    private AgentMessageMapper messageMapper;

    @BeforeEach
    void setUp() {
        messageMapper.delete(null);
        turnMapper.delete(null);
    }

    @Test
    void findCompletedContextBySessionIdShouldTrimByCompletedTurns() {
        String sessionId = "sess-1";
        String conversationId = "conv-1";

        insertTurn("turn-1", conversationId, sessionId, "req-1", "run-1", TurnStatus.COMPLETED, LocalDateTime.now().minusMinutes(4));
        insertTurn("turn-2", conversationId, sessionId, "req-2", "run-2", TurnStatus.COMPLETED, LocalDateTime.now().minusMinutes(3));
        insertTurn("turn-3", conversationId, sessionId, "req-3", "run-3", TurnStatus.COMPLETED, LocalDateTime.now().minusMinutes(2));
        insertTurn("turn-4", conversationId, sessionId, "req-4", "run-4", TurnStatus.FAILED, LocalDateTime.now().minusMinutes(1));

        insertUserMessage("msg-1", conversationId, sessionId, "turn-1", "run-1", 1L, "hello-1");
        insertUserMessage("msg-2", conversationId, sessionId, "turn-2", "run-2", 2L, "hello-2");
        insertUserMessage("msg-3", conversationId, sessionId, "turn-3", "run-3", 3L, "hello-3");
        insertUserMessage("msg-4", conversationId, sessionId, "turn-4", "run-4", 4L, "hello-4");

        List<Message> result = messageRepository.findCompletedContextBySessionId(sessionId, 2);

        assertEquals(2, result.size());
        assertEquals("turn-2", result.get(0).getTurnId());
        assertEquals("turn-3", result.get(1).getTurnId());
    }

    private void insertTurn(
        String turnId,
        String conversationId,
        String sessionId,
        String requestId,
        String runId,
        TurnStatus status,
        LocalDateTime createdAt
    ) {
        AgentTurnEntity entity = new AgentTurnEntity();
        entity.setTurnId(turnId);
        entity.setConversationId(conversationId);
        entity.setSessionId(sessionId);
        entity.setRequestId(requestId);
        entity.setRunId(runId);
        entity.setStatus(status);
        entity.setUserMessageId("msg-user-" + turnId);
        entity.setCreatedAt(createdAt);
        turnMapper.insert(entity);
    }

    private void insertUserMessage(
        String messageId,
        String conversationId,
        String sessionId,
        String turnId,
        String runId,
        long sequenceNo,
        String content
    ) {
        AgentMessageEntity entity = new AgentMessageEntity();
        entity.setMessageId(messageId);
        entity.setConversationId(conversationId);
        entity.setSessionId(sessionId);
        entity.setTurnId(turnId);
        entity.setRunId(runId);
        entity.setRole(MessageRole.USER);
        entity.setMessageType(MessageType.USER_INPUT);
        entity.setSequenceNo(sequenceNo);
        entity.setStatus(MessageStatus.COMPLETED);
        entity.setContentText(content);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        messageMapper.insert(entity);
    }
}

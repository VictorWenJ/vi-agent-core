package com.vi.agent.core.runtime.scope;

import com.vi.agent.core.common.exception.AgentRuntimeException;
import com.vi.agent.core.common.exception.ErrorCode;
import com.vi.agent.core.model.conversation.Conversation;
import com.vi.agent.core.model.conversation.ConversationStatus;
import com.vi.agent.core.model.port.SessionLockRepository;
import com.vi.agent.core.model.runtime.RunMetadata;
import com.vi.agent.core.model.session.Session;
import com.vi.agent.core.model.session.SessionMode;
import com.vi.agent.core.model.session.SessionResolutionResult;
import com.vi.agent.core.model.session.SessionStatus;
import com.vi.agent.core.runtime.command.RuntimeExecuteCommand;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.factory.MessageFactory;
import com.vi.agent.core.runtime.lifecycle.TurnLifecycleService;
import com.vi.agent.core.runtime.mdc.MdcScope;
import com.vi.agent.core.runtime.mdc.RuntimeMdcManager;
import com.vi.agent.core.runtime.support.TestFieldUtils;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeRunScopeManagerTest {

    @Test
    void openShouldRejectWhenLockAcquireFailed() {
        RuntimeRunScopeManager manager = new RuntimeRunScopeManager();
        StubSessionLockRepository lockRepository = new StubSessionLockRepository();
        lockRepository.tryLockResult = false;

        TestFieldUtils.setField(manager, "sessionLockRepository", lockRepository);
        TestFieldUtils.setField(manager, "turnLifecycleService", new StubTurnLifecycleService());
        TestFieldUtils.setField(manager, "runtimeMdcManager", new StubRuntimeMdcManager());
        TestFieldUtils.setField(manager, "messageFactory", new StubMessageFactory());

        AgentRuntimeException ex = assertThrows(AgentRuntimeException.class, () -> manager.open(buildContext()));
        assertEquals(ErrorCode.SESSION_CONCURRENT_REQUEST, ex.getErrorCode());
        assertEquals(0, lockRepository.unlockCount);
    }

    @Test
    void openShouldRejectAndUnlockWhenRunningTurnExists() {
        RuntimeRunScopeManager manager = new RuntimeRunScopeManager();
        StubSessionLockRepository lockRepository = new StubSessionLockRepository();
        StubTurnLifecycleService turnLifecycleService = new StubTurnLifecycleService();
        turnLifecycleService.existsRunningTurn = true;
        StubRuntimeMdcManager mdcManager = new StubRuntimeMdcManager();
        StubMessageFactory messageFactory = new StubMessageFactory();

        TestFieldUtils.setField(manager, "sessionLockRepository", lockRepository);
        TestFieldUtils.setField(manager, "turnLifecycleService", turnLifecycleService);
        TestFieldUtils.setField(manager, "runtimeMdcManager", mdcManager);
        TestFieldUtils.setField(manager, "messageFactory", messageFactory);

        AgentRuntimeException ex = assertThrows(AgentRuntimeException.class, () -> manager.open(buildContext()));
        assertEquals(ErrorCode.SESSION_CONCURRENT_REQUEST, ex.getErrorCode());
        assertEquals(1, lockRepository.unlockCount);
        assertEquals("sess-1", lockRepository.lastUnlockSessionId);
        assertEquals("run-1", lockRepository.lastUnlockRunId);
        assertEquals("sess-1", messageFactory.clearedSessionId);
        assertEquals(0, mdcManager.openCount);
    }

    @Test
    void closeShouldReleaseLockMdcAndSequenceCursor() {
        RuntimeRunScopeManager manager = new RuntimeRunScopeManager();
        StubSessionLockRepository lockRepository = new StubSessionLockRepository();
        StubTurnLifecycleService turnLifecycleService = new StubTurnLifecycleService();
        StubRuntimeMdcManager mdcManager = new StubRuntimeMdcManager();
        StubMessageFactory messageFactory = new StubMessageFactory();

        TestFieldUtils.setField(manager, "sessionLockRepository", lockRepository);
        TestFieldUtils.setField(manager, "turnLifecycleService", turnLifecycleService);
        TestFieldUtils.setField(manager, "runtimeMdcManager", mdcManager);
        TestFieldUtils.setField(manager, "messageFactory", messageFactory);

        RuntimeRunScope scope = manager.open(buildContext());
        assertNotNull(scope);
        assertEquals(0, lockRepository.unlockCount);
        assertEquals(1, mdcManager.openCount);

        scope.close();

        assertEquals(1, lockRepository.unlockCount);
        assertEquals("sess-1", lockRepository.lastUnlockSessionId);
        assertEquals("run-1", lockRepository.lastUnlockRunId);
        assertEquals("sess-1", messageFactory.clearedSessionId);
        assertEquals(1, mdcManager.scope.closeCount);
    }

    private static RuntimeExecutionContext buildContext() {
        RuntimeExecuteCommand command = RuntimeExecuteCommand.builder()
            .requestId("req-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .sessionMode(SessionMode.CONTINUE_EXACT_SESSION)
            .message("hello")
            .build();
        RuntimeExecutionContext context = RuntimeExecutionContext.create(command, null, false);
        context.setResolution(SessionResolutionResult.builder()
            .conversation(Conversation.builder()
                .conversationId("conv-1")
                .title("t")
                .status(ConversationStatus.ACTIVE)
                .activeSessionId("sess-1")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .lastMessageAt(Instant.now())
                .build())
            .session(Session.builder()
                .sessionId("sess-1")
                .conversationId("conv-1")
                .status(SessionStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build())
            .createdConversation(false)
            .createdSession(false)
            .build());
        context.setRunMetadata(RunMetadata.builder()
            .traceId("trace-1")
            .runId("run-1")
            .turnId("turn-1")
            .build());
        return context;
    }

    private static final class StubSessionLockRepository implements SessionLockRepository {
        private boolean tryLockResult = true;
        private int unlockCount = 0;
        private String lastUnlockSessionId;
        private String lastUnlockRunId;

        @Override
        public boolean tryLock(String sessionId, String runId, Duration ttl) {
            return tryLockResult;
        }

        @Override
        public void unlock(String sessionId, String runId) {
            unlockCount++;
            lastUnlockSessionId = sessionId;
            lastUnlockRunId = runId;
        }
    }

    private static final class StubTurnLifecycleService extends TurnLifecycleService {
        private boolean existsRunningTurn = false;

        @Override
        public boolean existsRunningTurn(String sessionId) {
            return existsRunningTurn;
        }
    }

    private static final class StubRuntimeMdcManager extends RuntimeMdcManager {
        private int openCount = 0;
        private TrackingMdcScope scope;

        @Override
        public MdcScope open(String requestId, String conversationId, String sessionId, RunMetadata runMetadata) {
            openCount++;
            scope = new TrackingMdcScope();
            return scope;
        }
    }

    private static final class TrackingMdcScope extends MdcScope {
        private int closeCount = 0;

        private TrackingMdcScope() {
            super(new HashMap<>());
        }

        @Override
        public void close() {
            closeCount++;
            super.close();
        }
    }

    private static final class StubMessageFactory extends MessageFactory {
        private String clearedSessionId;

        @Override
        public void clearSessionSequenceCursor(String sessionId) {
            clearedSessionId = sessionId;
        }
    }
}

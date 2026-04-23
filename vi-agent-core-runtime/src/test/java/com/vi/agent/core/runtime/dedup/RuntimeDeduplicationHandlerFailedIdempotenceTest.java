package com.vi.agent.core.runtime.dedup;

import com.vi.agent.core.model.runtime.RunStatus;
import com.vi.agent.core.model.session.SessionMode;
import com.vi.agent.core.model.turn.Turn;
import com.vi.agent.core.model.turn.TurnStatus;
import com.vi.agent.core.runtime.command.RuntimeExecuteCommand;
import com.vi.agent.core.runtime.event.RuntimeEvent;
import com.vi.agent.core.runtime.event.RuntimeEventType;
import com.vi.agent.core.runtime.execution.RuntimeExecutionContext;
import com.vi.agent.core.runtime.factory.AgentExecutionResultFactory;
import com.vi.agent.core.runtime.factory.RuntimeEventFactory;
import com.vi.agent.core.runtime.lifecycle.TurnDedupResult;
import com.vi.agent.core.runtime.lifecycle.TurnLifecycleService;
import com.vi.agent.core.runtime.result.AgentExecutionResult;
import com.vi.agent.core.runtime.support.TestFieldUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeDeduplicationHandlerFailedIdempotenceTest {

    @Test
    void requestIdHitFailedShouldReturnFailedWithoutThrowing() {
        RuntimeDeduplicationHandler handler = new RuntimeDeduplicationHandler();

        Turn failedTurn = Turn.builder()
            .turnId("turn-1")
            .conversationId("conv-1")
            .sessionId("sess-1")
            .requestId("req-1")
            .runId("run-1")
            .status(TurnStatus.FAILED)
            .userMessageId("msg-user-1")
            .createdAt(Instant.now())
            .build();
        failedTurn.markFailed("INVALID_MODEL_CONTEXT_MESSAGE", "invalid model context", Instant.now());

        StubTurnLifecycleService turnLifecycleService = new StubTurnLifecycleService();
        turnLifecycleService.turnDedupResult = TurnDedupResult.builder()
            .status(TurnStatus.FAILED)
            .turn(failedTurn)
            .build();

        TestFieldUtils.setField(handler, "turnLifecycleService", turnLifecycleService);
        TestFieldUtils.setField(handler, "agentExecutionResultFactory", new AgentExecutionResultFactory());
        TestFieldUtils.setField(handler, "runtimeEventFactory", new RuntimeEventFactory());

        List<RuntimeEvent> events = new ArrayList<>();
        RuntimeExecutionContext context = RuntimeExecutionContext.create(
            RuntimeExecuteCommand.builder()
                .requestId("req-1")
                .conversationId("conv-1")
                .sessionId("sess-1")
                .sessionMode(SessionMode.CONTINUE_EXACT_SESSION)
                .message("hello")
                .build(),
            events::add,
            false
        );

        AgentExecutionResult result = handler.checkAndBuildDedupAgentExecution(context);

        assertEquals(RunStatus.FAILED, result.getRunStatus());
        assertEquals(1, events.size());
        assertEquals(RuntimeEventType.RUN_FAILED, events.get(0).getEventType());
    }

    private static final class StubTurnLifecycleService extends TurnLifecycleService {
        private TurnDedupResult turnDedupResult;

        @Override
        public TurnDedupResult findAndBuildByRequestId(String requestId) {
            return turnDedupResult;
        }
    }
}

package com.vi.agent.core.model.memory;

import com.vi.agent.core.common.util.JsonUtils;
import com.vi.agent.core.model.context.WorkingMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StateDeltaTest {

    @Test
    void builderShouldKeepExplicitPatchFields() {
        StateDelta delta = StateDelta.builder()
            .sessionId("sess-1")
            .sourceRunId("run-1")
            .previousStateVersion(1L)
            .targetStateVersion(2L)
            .taskGoalOverride("更新任务目标")
            .workingModeOverride(WorkingMode.TASK_EXECUTION)
            .sourceCandidateId("candidate-1")
            .sourceCandidateId("candidate-2")
            .closeOpenLoopId("loop-1")
            .build();

        assertEquals("更新任务目标", delta.getTaskGoalOverride());
        assertEquals(WorkingMode.TASK_EXECUTION, delta.getWorkingModeOverride());
        assertEquals(List.of("candidate-1", "candidate-2"), delta.getSourceCandidateIds());
        assertThrows(UnsupportedOperationException.class, () -> delta.getSourceCandidateIds().add("candidate-3"));
    }

    @Test
    void shouldSerializeAndDeserializePatchFields() {
        StateDelta delta = StateDelta.builder()
            .sessionId("sess-1")
            .sourceRunId("run-1")
            .previousStateVersion(1L)
            .targetStateVersion(2L)
            .taskGoalOverride("更新任务目标")
            .workingModeOverride(WorkingMode.TASK_EXECUTION)
            .sourceCandidateId("candidate-1")
            .build();

        StateDelta restored = JsonUtils.jsonToBean(JsonUtils.toJson(delta), StateDelta.class);

        assertEquals("更新任务目标", restored.getTaskGoalOverride());
        assertEquals(WorkingMode.TASK_EXECUTION, restored.getWorkingModeOverride());
        assertEquals(List.of("candidate-1"), restored.getSourceCandidateIds());
    }
}
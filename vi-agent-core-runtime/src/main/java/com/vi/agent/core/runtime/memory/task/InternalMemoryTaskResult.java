package com.vi.agent.core.runtime.memory.task;

import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.memory.InternalTaskStatus;
import com.vi.agent.core.model.memory.InternalTaskType;
import com.vi.agent.core.model.memory.StateDelta;
import lombok.Builder;
import lombok.Getter;

/**
 * Result of an internal memory task audit execution.
 */
@Getter
@Builder(toBuilder = true)
public class InternalMemoryTaskResult {

    private final String internalTaskId;

    private final InternalTaskType taskType;

    private final InternalTaskStatus status;

    private final boolean success;

    private final boolean degraded;

    private final boolean skipped;

    private final StateDelta stateDelta;

    private final ConversationSummary summary;

    private final String failureReason;

    private final String inputJson;

    private final String outputJson;
}

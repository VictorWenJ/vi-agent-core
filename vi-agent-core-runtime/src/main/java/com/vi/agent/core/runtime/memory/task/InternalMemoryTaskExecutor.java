package com.vi.agent.core.runtime.memory.task;

/**
 * Executes an internal memory task after its audit record has entered RUNNING.
 */
@FunctionalInterface
public interface InternalMemoryTaskExecutor {

    InternalMemoryTaskResult execute(String internalTaskId, String inputJson);
}

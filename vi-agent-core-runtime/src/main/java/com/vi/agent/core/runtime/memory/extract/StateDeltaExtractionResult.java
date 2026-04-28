package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.model.memory.StateDelta;
import com.vi.agent.core.model.llm.StructuredOutputChannelResult;
import com.vi.agent.core.model.prompt.PromptRenderMetadata;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * Result of a StateDelta extraction attempt.
 */
@Getter
@Builder(toBuilder = true)
public class StateDeltaExtractionResult {

    private final boolean success;

    private final boolean degraded;

    private final StateDelta stateDelta;

    private final String rawOutput;

    private final String failureReason;

    @Singular("sourceCandidateId")
    private final List<String> sourceCandidateIds;

    /** 本次 prompt 渲染审计元数据。 */
    private final PromptRenderMetadata promptRenderMetadata;

    /** Provider 结构化输出通道结果。 */
    private final StructuredOutputChannelResult structuredOutputChannelResult;
}

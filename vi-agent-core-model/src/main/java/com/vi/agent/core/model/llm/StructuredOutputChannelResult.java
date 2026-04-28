package com.vi.agent.core.model.llm;

import com.vi.agent.core.model.prompt.StructuredLlmOutputMode;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * 结构化输出通道执行结果。
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class StructuredOutputChannelResult {

    /** 结构化输出通道是否成功。 */
    Boolean success;

    /** 成功时归一化后的结构化输出。 */
    NormalizedStructuredLlmOutput output;

    /** Provider 实际使用的结构化输出模式。 */
    StructuredLlmOutputMode actualStructuredOutputMode;

    /** 同模式重试次数，P2-E 当前固定为 0。 */
    Integer retryCount;

    /** 失败原因。 */
    String failureReason;
}

package com.vi.agent.core.runtime.memory.extract;

import com.vi.agent.core.model.memory.ConversationSummary;
import com.vi.agent.core.model.llm.StructuredOutputChannelResult;
import com.vi.agent.core.model.prompt.PromptRenderMetadata;
import lombok.Builder;
import lombok.Getter;

/**
 * 会话摘要抽取结果。
 */
@Getter
@Builder(toBuilder = true)
public class ConversationSummaryExtractionResult {

    /** 是否成功完成抽取流程。 */
    private final boolean success;

    /** 是否发生降级。 */
    private final boolean degraded;

    /** 是否跳过摘要更新。 */
    private final boolean skipped;

    /** 模型生成的摘要内容草稿，系统字段由协调器补齐后再持久化。 */
    private final ConversationSummary conversationSummary;

    /** 模型原始输出。 */
    private final String rawOutput;

    /** 失败或降级原因。 */
    private final String failureReason;

    /** 生成摘要的模型供应商。 */
    private final String generatorProvider;

    /** 生成摘要的模型名称。 */
    private final String generatorModel;

    /** 本次 prompt 渲染审计元数据。 */
    private final PromptRenderMetadata promptRenderMetadata;

    /** Provider 结构化输出通道结果。 */
    private final StructuredOutputChannelResult structuredOutputChannelResult;
}

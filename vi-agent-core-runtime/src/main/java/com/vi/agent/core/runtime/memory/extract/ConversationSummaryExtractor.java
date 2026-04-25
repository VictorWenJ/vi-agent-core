package com.vi.agent.core.runtime.memory.extract;

/**
 * 会话摘要抽取器。
 */
public interface ConversationSummaryExtractor {

    /**
     * 基于已完成回合 transcript 抽取下一版会话摘要内容。
     *
     * @param command 摘要抽取命令
     * @return 摘要抽取结果
     */
    ConversationSummaryExtractionResult extract(ConversationSummaryExtractionCommand command);
}

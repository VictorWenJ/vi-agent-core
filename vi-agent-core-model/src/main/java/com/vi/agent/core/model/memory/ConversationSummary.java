package com.vi.agent.core.model.memory;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 已退出 working set 的历史摘要。
 */
@Getter
@Builder
public class ConversationSummary {

    /** 会话摘要 ID。 */
    private final String summaryId;

    /** 当前 session ID。 */
    private final String sessionId;

    /** 摘要版本。 */
    private final Long summaryVersion;

    /** 摘要覆盖的起始消息序号。 */
    private final Long coveredFromSequenceNo;

    /** 摘要覆盖的结束消息序号。 */
    private final Long coveredToSequenceNo;

    /** 摘要正文。 */
    private final String summaryText;

    /** 摘要模板 key。 */
    private final String summaryTemplateKey;

    /** 摘要模板版本。 */
    private final String summaryTemplateVersion;

    /** 生成摘要的 provider。 */
    private final String generatorProvider;

    /** 生成摘要的模型。 */
    private final String generatorModel;

    /** 产生该摘要的 run ID。 */
    private final String sourceRunId;

    /** 摘要创建时间。 */
    private final Instant createdAt;
}

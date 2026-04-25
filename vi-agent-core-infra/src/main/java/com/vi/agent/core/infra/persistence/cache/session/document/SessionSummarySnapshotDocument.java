package com.vi.agent.core.infra.persistence.cache.session.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Session Summary Redis snapshot cache 文档对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSummarySnapshotDocument {

    /** summary ID。 */
    private String summaryId;

    /** session ID。 */
    private String sessionId;

    /** summary 版本号。 */
    private Long summaryVersion;

    /** 摘要覆盖的起始消息序号。 */
    private Long coveredFromSequenceNo;

    /** 摘要覆盖的结束消息序号。 */
    private Long coveredToSequenceNo;

    /** 摘要正文。 */
    private String summaryText;

    /** summary prompt 模板 key。 */
    private String summaryTemplateKey;

    /** summary prompt 模板版本。 */
    private String summaryTemplateVersion;

    /** 生成 summary 的 provider。 */
    private String generatorProvider;

    /** 生成 summary 的 model。 */
    private String generatorModel;

    /** snapshot cache 格式版本。 */
    private Integer snapshotVersion;

    /** 创建时间毫秒时间戳。 */
    private Long createdAtEpochMs;
}

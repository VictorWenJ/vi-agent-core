package com.vi.agent.core.model.context;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * WorkingContext 来源版本信息。
 */
@Getter
@Builder
public class WorkingContextSource {

    /** transcript 快照版本。 */
    private final Long transcriptSnapshotVersion;

    /** working set 版本。 */
    private final Long workingSetVersion;

    /** session state 版本。 */
    private final Long stateVersion;

    /** conversation summary 版本。 */
    private final Long summaryVersion;

    /** 参与本次上下文装配的 evidence ID 列表。 */
    @Singular("evidenceId")
    private final List<String> evidenceIds;
}

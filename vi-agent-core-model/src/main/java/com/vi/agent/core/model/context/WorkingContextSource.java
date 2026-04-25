package com.vi.agent.core.model.context;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * WorkingContext 来源版本信息。
 */
@Getter
@Builder
@Jacksonized
public class WorkingContextSource {

    /** transcript 快照版本。 */
    private final Long transcriptSnapshotVersion;

    /** working set 版本。 */
    private final Long workingSetVersion;

    /** session state 版本。 */
    private final Long stateVersion;

    /** conversation summary 版本。 */
    private final Long summaryVersion;

    /** 参与本次上下文装配的 artifact/reference 快照 ID 列表。 */
    @Singular("artifactSnapshotId")
    private final List<String> artifactSnapshotIds;
}
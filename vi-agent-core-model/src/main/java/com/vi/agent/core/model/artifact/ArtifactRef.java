package com.vi.agent.core.model.artifact;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Artifact 引用信息。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactRef {

    /** Artifact 唯一 ID。 */
    private String artifactId;

    /** Artifact 类型。 */
    private String artifactType;

    /** Artifact 存储地址。 */
    private String uri;
}

package com.vi.agent.core.model.artifact;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Artifact 引用信息。
 */
@Data
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

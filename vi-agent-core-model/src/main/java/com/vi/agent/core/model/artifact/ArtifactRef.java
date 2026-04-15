package com.vi.agent.core.model.artifact;

/**
 * Artifact 引用信息。
 */
public class ArtifactRef {

    /** Artifact 唯一 ID。 */
    private String artifactId;

    /** Artifact 类型。 */
    private String artifactType;

    /** Artifact 存储地址。 */
    private String uri;

    public ArtifactRef() {
    }

    public ArtifactRef(String artifactId, String artifactType, String uri) {
        this.artifactId = artifactId;
        this.artifactType = artifactType;
        this.uri = uri;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getArtifactType() {
        return artifactType;
    }

    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }
}

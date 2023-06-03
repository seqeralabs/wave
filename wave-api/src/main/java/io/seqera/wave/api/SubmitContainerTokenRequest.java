package io.seqera.wave.api;


import java.time.OffsetDateTime;

/**
 * Model a request for an augmented container
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class SubmitContainerTokenRequest {

    /**
     * Tower access token required to enable the service
     */
   public  String towerAccessToken;

    /**
     * Tower refresh token used to refresh the authorization
     */
    public String towerRefreshToken;

    /**
     * Tower endpoint: the public address
     * of the tower instance to integrate with wave
     */
    public String towerEndpoint;

    /**
     * Tower workspace id
     */
    public Long towerWorkspaceId;

    /**
     * Container image to be pulled
     */
    public String containerImage;

    /**
     * Container build file i.g. Dockerfile of the container to be build
     */
    public String containerFile;

    /**
     * List of layers to be added in the pulled image
     */
    public ContainerConfig containerConfig;

    /**
     * Conda recipe file used to build the container
     */
    public String condaFile;

    /**
     * Spack recipe file used to build the container
     */
    public String spackFile;

    /**
     * The container platform to be used
     */
    public String containerPlatform;

    /**
     * The repository where the build container should be pushed
     */
    public String buildRepository;

    /**
     * The repository where the build container should be pushed
     */
    public String cacheRepository;

    /**
     * Request timestamp
     */
    public String timestamp;

    /**
     * Request unique fingerprint
     */
    public String fingerprint;

    /**
     * Force a build even when a cached image already exists
     */
    public boolean forceBuild;

    public SubmitContainerTokenRequest withTowerAccessToken(String token) {
        this.towerAccessToken = token;
        return this;
    }

    public SubmitContainerTokenRequest withTowerRefreshToken(String token) {
        this.towerRefreshToken = token;
        return this;
    }

    public SubmitContainerTokenRequest withTowerEndpoint(String endpoint) {
        this.towerEndpoint = endpoint;
        return this;
    }

    public SubmitContainerTokenRequest withTowerWorkspaceId(Long workspaceId) {
        this.towerWorkspaceId = workspaceId;
        return this;
    }

    public SubmitContainerTokenRequest withContainerImage(String containerImage) {
        this.containerImage = containerImage;
        return this;
    }

    public SubmitContainerTokenRequest withContainerFile(String containerFile) {
        this.containerFile = containerFile;
        return this;
    }

    public SubmitContainerTokenRequest withContainerConfig(ContainerConfig config) {
        this.containerConfig = config;
        return this;
    }

    public SubmitContainerTokenRequest withCondaFile(String condaFile) {
        this.condaFile = condaFile;
        return this;
    }

    public SubmitContainerTokenRequest withSpackFile(String spackFile) {
        this.spackFile = spackFile;
        return this;
    }

    public SubmitContainerTokenRequest withContainerPlatform(String platform) {
        this.containerPlatform = platform;
        return this;
    }

    public SubmitContainerTokenRequest withBuildRepository(String buildRepository) {
        this.buildRepository = buildRepository;
        return this;
    }

    public SubmitContainerTokenRequest withCacheRepository(String cacheRepository) {
        this.cacheRepository = cacheRepository;
        return this;
    }

    public SubmitContainerTokenRequest withTimestamp(String timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    public SubmitContainerTokenRequest withTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp.toString();
        return this;
    }

    public SubmitContainerTokenRequest withFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
        return this;
    }
}


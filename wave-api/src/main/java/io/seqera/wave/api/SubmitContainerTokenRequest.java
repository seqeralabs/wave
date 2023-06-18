package io.seqera.wave.api;


import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Model a request for an augmented container
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class SubmitContainerTokenRequest implements Cloneable {

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

    /**
     * Request a sealed Wave container that cause the build of a container including
     * all {@link #containerConfig} dependencies
     */
    public boolean sealedMode;

    public SubmitContainerTokenRequest copyWith(Map opts) {
        try {
            final SubmitContainerTokenRequest copy = (SubmitContainerTokenRequest) this.clone();
            if( opts.containsKey("towerAccessToken") )
                copy.towerAccessToken = (String)opts.get("towerAccessToken");
            if( opts.containsKey("towerRefreshToken") )
                copy.towerRefreshToken = (String)opts.get("towerRefreshToken");
            if( opts.containsKey("towerEndpoint") )
                copy.towerEndpoint = (String)opts.get("towerEndpoint");
            if( opts.containsKey("towerWorkspaceId") )
                copy.towerWorkspaceId = Long.valueOf(opts.get("towerWorkspaceId").toString());
            if( opts.containsKey("containerImage") )
                copy.containerImage = (String)opts.get("containerImage");
            if( opts.containsKey("containerFile") )
                copy.containerFile = (String)opts.get("containerFile");
            if( opts.containsKey("containerConfig") )
                copy.containerConfig = (ContainerConfig)opts.get("containerConfig");
            if( opts.containsKey("condaFile") )
                copy.condaFile = (String)opts.get("condaFile");
            if( opts.containsKey("spackFile") )
                copy.spackFile = (String)opts.get("spackFile");
            if( opts.containsKey("containerPlatform") )
                copy.containerPlatform = (String)opts.get("containerPlatform");
            if( opts.containsKey("buildRepository") )
                copy.buildRepository = (String)opts.get("buildRepository");
            if( opts.containsKey("cacheRepository") )
                copy.cacheRepository = (String)opts.get("cacheRepository");
            if( opts.containsKey("timestamp") )
                copy.timestamp = (String)opts.get("timestamp");
            if( opts.containsKey("fingerprint") )
                copy.fingerprint = (String)opts.get("fingerprint");
            if( opts.containsKey("forceBuild") )
                copy.forceBuild = (boolean)opts.get("forceBuild");
            if( opts.containsKey("sealedMode") )
                copy.sealedMode = (boolean)opts.get("sealedMode");
            // done
            return copy;
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

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

    public SubmitContainerTokenRequest withSealedMode(boolean value) {
        this.sealedMode = value;
        return this;
    }
}


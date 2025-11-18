/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.wave.api;


import java.time.OffsetDateTime;
import java.util.List;
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
    @Deprecated
    public String condaFile;

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
     * Enable freeze mode that cause the container build to include
     * all {@link #containerConfig} dependencies
     */
    public boolean freeze;

    /**
     * A layer holding the build context for this container request
     */
    public BuildContext buildContext;

    /**
     * Format of the target container build. Use `sif` for Singularity. default: docker/oci
     */
    public String format;

    /**
     * When {@code true} build requests are carried out in dry-run mode.
     * Id of workflow in tower
     */
    public Boolean dryRun;

    /**
     * Id of compute workflow environment in tower
     */
    public String workflowId;

    /**
     * One or more container should be included in upstream container request
     */
    public List<String> containerIncludes;

    /**
     * Defines the packages to be included in this container request
     */
    public PackagesSpec packages;

    /**
     * The strategy applied to name a container build by wave when using
     * the freeze option.
     */
    public ImageNameStrategy nameStrategy;

    /**
     * Whenever use container "mirror" mode
     */
    public boolean mirror;

    /**
     * The request security scan mode
     */
    public ScanMode scanMode;

    /**
     * Define the allows security vulnerabilities in the container request.
     * Empty or null means no vulnerabilities are allowed.
     */
    public List<ScanLevel> scanLevels;

    /**
     * The compression mode to be used when building a container image
     */
    public BuildCompression buildCompression;

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
            if( opts.containsKey("freeze") )
                copy.freeze = (boolean)opts.get("freeze");
            if( opts.containsKey("buildContext") )
                copy.buildContext = (BuildContext) opts.get("buildContext");
            if( opts.containsKey("format") )
                copy.format = (String) opts.get("format");
            if( opts.containsKey("dryRun") )
                copy.dryRun = (Boolean) opts.get("dryRun");
            if( opts.containsKey("workflowId") )
                copy.workflowId = (String) opts.get("workflowId");
            if( opts.containsKey("containerIncludes"))
                copy.containerIncludes = (List<String>) opts.get("containerIncludes");
            if( opts.containsKey("packages"))
                copy.packages = (PackagesSpec) opts.get("packages");
            if( opts.containsKey("nameStrategy"))
                copy.nameStrategy = (ImageNameStrategy) opts.get("nameStrategy");
            if( opts.containsKey("mirror") )
                copy.mirror = (boolean) opts.get("mirror");
            if( opts.containsKey("scanMode") )
                copy.scanMode = (ScanMode) opts.get("scanMode");
            if( opts.containsKey("scanLevels"))
                copy.scanLevels = (List<ScanLevel>) opts.get("scanLevels");
            if( opts.containsKey("buildCompression"))
                copy.buildCompression = (BuildCompression) opts.get("buildCompression");
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

    @Deprecated
    public SubmitContainerTokenRequest withCondaFile(String condaFile) {
        this.condaFile = condaFile;
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

    public SubmitContainerTokenRequest withFreezeMode(boolean value) {
        this.freeze = value;
        return this;
    }

    public SubmitContainerTokenRequest withBuildContext(BuildContext context) {
        this.buildContext = context;
        return this;
    }

    public SubmitContainerTokenRequest withFormat(String value) {
        this.format = value;
        return this;
    }

    public SubmitContainerTokenRequest withDryRun(Boolean dryRun) {
        this.dryRun = dryRun;
        return this;
    }

    public SubmitContainerTokenRequest withWorkflowId(String workflowId) {
        this.workflowId = workflowId;
        return this;
    }

    public SubmitContainerTokenRequest withContainerIncludes(List<String> containerIncludes) {
        this.containerIncludes = containerIncludes;
        return this;
    }

    public SubmitContainerTokenRequest withPackages(PackagesSpec packages) {
        this.packages = packages;
        return this;
    }

    public SubmitContainerTokenRequest withNameStrategy(ImageNameStrategy value) {
        this.nameStrategy = value;
        return this;
    }

    public SubmitContainerTokenRequest withMirror(boolean value) {
        this.mirror = value;
        return this;
    }

    public SubmitContainerTokenRequest withScanMode(ScanMode mode) {
        this.scanMode = mode;
        return this;
    }

    public SubmitContainerTokenRequest withScanLevels(List<ScanLevel> levels) {
        this.scanLevels = levels;
        return this;
    }

    public SubmitContainerTokenRequest withBuildCompression(BuildCompression mode) {
        this.buildCompression = mode;
        return this;
    }

    public boolean formatSingularity() {
        return "sif".equals(format);
    }

    @Override
    public String toString() {
        return "SubmitContainerTokenRequest{" +
                "towerAccessToken='" + ObjectUtils.redact(towerAccessToken) + '\'' +
                ", towerRefreshToken='" + ObjectUtils.redact(towerRefreshToken) + '\'' +
                ", towerEndpoint='" + towerEndpoint + '\'' +
                ", towerWorkspaceId=" + towerWorkspaceId +
                ", containerImage='" + containerImage + '\'' +
                ", containerFile='" + containerFile + '\'' +
                ", containerConfig=" + containerConfig +
                ", condaFile='" + condaFile + '\'' +
                ", containerPlatform='" + containerPlatform + '\'' +
                ", buildRepository='" + buildRepository + '\'' +
                ", cacheRepository='" + cacheRepository + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", fingerprint='" + fingerprint + '\'' +
                ", freeze=" + freeze +
                ", buildContext=" + buildContext +
                ", type=" + format +
                ", dryRun=" + dryRun +
                ", workflowId=" + workflowId +
                ", containerIncludes=" + ObjectUtils.toString(containerIncludes) +
                ", packages=" + packages +
                ", nameStrategy=" + nameStrategy +
                ", mirror=" + mirror +
                ", scanMode=" + scanMode +
                ", scanLevels=" + scanLevels +
                ", buildCompression=" + buildCompression +
                '}';
    }
}

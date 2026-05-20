/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.controller.v1.mapper

import groovy.transform.CompileStatic

import io.seqera.wave.api.BuildContext as InternalBuildContext
import io.seqera.wave.api.ContainerConfig as InternalContainerConfig
import io.seqera.wave.api.ContainerLayer as InternalContainerLayer
import io.seqera.wave.api.ContainerStatus as InternalContainerStatus
import io.seqera.wave.api.ContainerStatusResponse as InternalContainerStatusResponse
import io.seqera.wave.api.ImageNameStrategy as InternalImageNameStrategy
import io.seqera.wave.api.PackagesSpec as InternalPackagesSpec
import io.seqera.wave.api.ScanLevel as InternalScanLevel
import io.seqera.wave.api.ScanMode as InternalScanMode
import io.seqera.wave.api.SubmitContainerTokenRequest as InternalSubmitRequest
import io.seqera.wave.api.SubmitContainerTokenResponse as InternalSubmitResponse
import io.seqera.wave.api.v1.model.ContainerConfig as V1ContainerConfig
import io.seqera.wave.api.v1.model.ContainerLayer as V1ContainerLayer
import io.seqera.wave.api.v1.model.ContainerRequest as V1ContainerRequest
import io.seqera.wave.api.v1.model.ContainerRequestFormat as V1ContainerRequestFormat
import io.seqera.wave.api.v1.model.ContainerRequestNameStrategy as V1ContainerRequestNameStrategy
import io.seqera.wave.api.v1.model.ContainerResponse as V1ContainerResponse
import io.seqera.wave.api.v1.model.ContainerStatus as V1ContainerStatus
import io.seqera.wave.api.v1.model.ContainerStatusResponse as V1ContainerStatusResponse
import io.seqera.wave.api.v1.model.PackagesSpec as V1PackagesSpec
import io.seqera.wave.api.v1.model.PackagesSpecType as V1PackagesSpecType
import io.seqera.wave.api.v1.model.ScanLevel as V1ScanLevel
import io.seqera.wave.api.v1.model.ScanMode as V1ScanMode
import io.seqera.wave.api.v1.model.User as V1User
import io.seqera.wave.api.v1.model.Vulnerability as V1Vulnerability
import io.seqera.wave.api.v1.model.WaveContainerRecord as V1WaveContainerRecord
import io.seqera.wave.config.CondaOpts as InternalCondaOpts
import io.seqera.wave.config.CranOpts as InternalCranOpts
import io.seqera.wave.config.PixiOpts as InternalPixiOpts
import io.seqera.wave.api.v1.model.CondaOpts as V1CondaOpts
import io.seqera.wave.api.v1.model.CranOpts as V1CranOpts
import io.seqera.wave.api.v1.model.PixiOpts as V1PixiOpts
import io.seqera.wave.service.persistence.WaveContainerRecord as InternalWaveContainerRecord
import io.seqera.wave.tower.User as InternalUser

/**
 * Maps between v1 API model objects and internal model objects for the containers API.
 *
 * Field-level divergences (documented):
 *   - ContainerLayer.gzipSize: internal=Integer, v1=String (converted via toString/parseInt)
 *   - ContainerRequest.format: internal=String, v1=ContainerRequestFormat enum (converted via value)
 *   - ContainerRequest.nameStrategy: internal=ImageNameStrategy enum (lowercase), v1=ContainerRequestNameStrategy enum (mixed case JSON values)
 *   - ContainerRequest.scanMode: internal=ScanMode enum (lowercase), v1=ScanMode enum (lowercase JSON values)
 *   - ContainerRequest.scanLevels: internal=ScanLevel enum (uppercase), v1=ScanLevel enum (uppercase JSON values)
 *   - PackagesSpec.type: internal=PackagesSpec.Type (CONDA/CRAN), v1=PackagesSpecType (CONDA/CRAN) - same names, cross-enum mapping
 *   - WaveContainerRecord.timestamp/expiration: internal=Instant, v1=String (toString)
 *   - WaveContainerRecord.user: internal=io.seqera.wave.tower.User, v1=io.seqera.wave.api.v1.model.User
 *   - ContainerStatusResponse.creationTime: internal=Instant, v1=String (toString)
 *   - ContainerStatusResponse.duration: internal=Duration, v1=String (toString)
 *   - ContainerStatusResponse.vulnerabilities: internal=Map<String,Integer>, v1=Map<String,Vulnerability> (count mapped)
 *   - ContainerResponse.status: v1 has ContainerStatus, internal has Boolean succeeded (mapped via succeeded flag)
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class ContainersV1Mapper {

    // -------------------------------------------------------------------------
    // Request mapping: v1 → internal
    // -------------------------------------------------------------------------

    static InternalSubmitRequest toInternalRequest(V1ContainerRequest v1) {
        if( v1 == null )
            return null
        final req = new InternalSubmitRequest()
        req.containerImage = v1.containerImage
        req.containerFile = v1.containerFile
        req.containerPlatform = v1.containerPlatform
        req.buildRepository = v1.buildRepository
        req.cacheRepository = v1.cacheRepository
        req.timestamp = v1.timestamp
        req.fingerprint = v1.fingerprint
        req.freeze = v1.freeze != null ? v1.freeze.booleanValue() : false
        req.dryRun = v1.dryRun
        req.workflowId = v1.workflowId
        req.containerIncludes = v1.containerIncludes
        req.towerAccessToken = v1.towerAccessToken
        req.towerRefreshToken = v1.towerRefreshToken
        req.towerEndpoint = v1.towerEndpoint
        req.towerWorkspaceId = v1.towerWorkspaceId
        req.buildTemplate = v1.buildTemplate
        req.mirror = v1.mirror != null ? v1.mirror.booleanValue() : false
        req.format = toInternalFormat(v1.format)
        req.nameStrategy = toInternalNameStrategy(v1.nameStrategy)
        req.scanMode = toInternalScanMode(v1.scanMode)
        req.scanLevels = toInternalScanLevels(v1.scanLevels)
        req.containerConfig = toInternalContainerConfig(v1.containerConfig)
        req.buildContext = toInternalBuildContext(v1.buildContext)
        req.packages = toInternalPackagesSpec(v1.packages)
        return req
    }

    // -------------------------------------------------------------------------
    // Response mapping: internal → v1
    // -------------------------------------------------------------------------

    static V1ContainerResponse toV1Response(InternalSubmitResponse internal) {
        if( internal == null )
            return null
        return new V1ContainerResponse()
                .requestId(internal.requestId)
                .containerToken(internal.containerToken)
                .targetImage(internal.targetImage)
                .expiration(internal.expiration?.toString())
                .containerImage(internal.containerImage)
                .buildId(internal.buildId)
                .cached(internal.cached)
                .freeze(internal.freeze)
                .mirror(internal.mirror)
                .scanId(internal.scanId)
                .status(toV1ContainerStatus(internal.succeeded))
    }

    static V1WaveContainerRecord toV1Record(InternalWaveContainerRecord internal) {
        if( internal == null )
            return null
        return new V1WaveContainerRecord()
                .user(toV1User(internal.user))
                .workspaceId(internal.workspaceId)
                .containerConfig(toV1ContainerConfig(internal.containerConfig))
                .timestamp(internal.timestamp?.toString())
                .expiration(internal.expiration?.toString())
                .containerImage(internal.containerImage)
                .containerFile(internal.containerFile)
                .condaFile(internal.condaFile)
                .platform(internal.platform)
                .towerEndpoint(internal.towerEndpoint)
                .buildRepository(internal.buildRepository)
                .cacheRepository(internal.cacheRepository)
                .fingerprint(internal.fingerprint)
                .zoneId(internal.zoneId)
                .ipAddress(internal.ipAddress)
                .sourceImage(internal.sourceImage)
                .sourceDigest(internal.sourceDigest)
                .waveImage(internal.waveImage)
                .waveDigest(internal.waveDigest)
                .buildId(internal.buildId)
                .buildNew(internal.buildNew)
                .freeze(internal.freeze)
                .fusionVersion(internal.fusionVersion)
    }

    static V1ContainerStatusResponse toV1Status(InternalContainerStatusResponse internal) {
        if( internal == null )
            return null
        return new V1ContainerStatusResponse()
                .id(internal.id)
                .status(toV1ContainerStatusEnum(internal.status))
                .buildId(internal.buildId)
                .mirrorId(internal.mirrorId)
                .scanId(internal.scanId)
                .vulnerabilities(toV1Vulnerabilities(internal.vulnerabilities))
                .succeeded(internal.succeeded)
                .reason(internal.reason)
                .detailsUri(internal.detailsUri)
                .creationTime(internal.creationTime?.toString())
                .duration(internal.duration?.toString())
    }

    // -------------------------------------------------------------------------
    // Nested type helpers: v1 → internal
    // -------------------------------------------------------------------------

    private static String toInternalFormat(V1ContainerRequestFormat format) {
        if( format == null )
            return null
        return format.value
    }

    private static InternalImageNameStrategy toInternalNameStrategy(V1ContainerRequestNameStrategy v1) {
        if( v1 == null )
            return null
        return InternalImageNameStrategy.valueOf(v1.value)
    }

    private static InternalScanMode toInternalScanMode(V1ScanMode v1) {
        if( v1 == null )
            return null
        return InternalScanMode.valueOf(v1.value)
    }

    private static List<InternalScanLevel> toInternalScanLevels(List<V1ScanLevel> v1Levels) {
        if( v1Levels == null )
            return null
        return v1Levels.collect { V1ScanLevel v1 -> InternalScanLevel.valueOf(v1.value) }
    }

    private static InternalContainerConfig toInternalContainerConfig(V1ContainerConfig v1) {
        if( v1 == null )
            return null
        final layers = v1.layers?.collect { V1ContainerLayer v1Layer -> toInternalContainerLayer(v1Layer) }
        return new InternalContainerConfig(v1.entrypoint, v1.cmd, v1.env, v1.workingDir, layers)
    }

    private static InternalContainerLayer toInternalContainerLayer(V1ContainerLayer v1) {
        if( v1 == null )
            return null
        // v1 gzipSize is String, internal is Integer
        final Integer gzipSize = v1.gzipSize != null ? Integer.parseInt(v1.gzipSize) : null
        final layer = new InternalContainerLayer(v1.location, v1.gzipDigest, gzipSize, v1.tarDigest)
        layer.skipHashing = v1.skipHashing
        return layer
    }

    private static InternalBuildContext toInternalBuildContext(V1ContainerLayer v1) {
        if( v1 == null )
            return null
        final Integer gzipSize = v1.gzipSize != null ? Integer.parseInt(v1.gzipSize) : null
        final layer = new InternalContainerLayer(v1.location, v1.gzipDigest, gzipSize, v1.tarDigest)
        return InternalBuildContext.of(layer)
    }

    private static InternalPackagesSpec toInternalPackagesSpec(V1PackagesSpec v1) {
        if( v1 == null )
            return null
        final spec = new InternalPackagesSpec()
        spec.type = toInternalPackagesSpecType(v1.type)
        spec.environment = v1.environment
        spec.entries = v1.entries
        spec.channels = v1.channels
        spec.condaOpts = toInternalCondaOpts(v1.condaOpts)
        spec.cranOpts = toInternalCranOpts(v1.cranOpts)
        spec.pixiOpts = toInternalPixiOpts(v1.pixiOpts)
        return spec
    }

    private static InternalPackagesSpec.Type toInternalPackagesSpecType(V1PackagesSpecType v1) {
        if( v1 == null )
            return null
        return InternalPackagesSpec.Type.valueOf(v1.value)
    }

    private static InternalCondaOpts toInternalCondaOpts(V1CondaOpts v1) {
        if( v1 == null )
            return null
        final opts = new InternalCondaOpts()
        opts.mambaImage = v1.mambaImage
        opts.basePackages = v1.basePackages
        opts.commands = v1.commands
        opts.baseImage = v1.baseImage
        return opts
    }

    private static InternalCranOpts toInternalCranOpts(V1CranOpts v1) {
        if( v1 == null )
            return null
        final opts = new InternalCranOpts()
        opts.rImage = v1.rimage
        opts.basePackages = v1.basePackages
        opts.commands = v1.commands
        return opts
    }

    private static InternalPixiOpts toInternalPixiOpts(V1PixiOpts v1) {
        if( v1 == null )
            return null
        final opts = new InternalPixiOpts()
        opts.pixiImage = v1.pixiImage
        opts.basePackages = v1.basePackages
        opts.commands = v1.commands
        opts.baseImage = v1.baseImage
        return opts
    }

    // -------------------------------------------------------------------------
    // Nested type helpers: internal → v1
    // -------------------------------------------------------------------------

    private static V1ContainerConfig toV1ContainerConfig(InternalContainerConfig internal) {
        if( internal == null )
            return null
        final layers = internal.layers?.collect { InternalContainerLayer l -> toV1ContainerLayer(l) }
        return new V1ContainerConfig()
                .entrypoint(internal.entrypoint)
                .cmd(internal.cmd)
                .env(internal.env)
                .workingDir(internal.workingDir)
                .layers(layers)
    }

    private static V1ContainerLayer toV1ContainerLayer(InternalContainerLayer internal) {
        if( internal == null )
            return null
        // internal gzipSize is Integer, v1 is String
        return new V1ContainerLayer()
                .location(internal.location)
                .gzipDigest(internal.gzipDigest)
                .gzipSize(internal.gzipSize?.toString())
                .tarDigest(internal.tarDigest)
                .skipHashing(internal.skipHashing)
    }

    private static V1User toV1User(InternalUser internal) {
        if( internal == null )
            return null
        return new V1User()
                .id(internal.id)
                .userName(internal.userName)
                .email(internal.email)
    }

    private static V1ContainerStatus toV1ContainerStatus(Boolean succeeded) {
        // Map the boolean succeeded to ContainerStatus:
        // null / false => PENDING (or result not yet known)
        // true         => DONE
        if( succeeded == null )
            return V1ContainerStatus.PENDING
        return succeeded ? V1ContainerStatus.DONE : V1ContainerStatus.BUILDING
    }

    private static V1ContainerStatus toV1ContainerStatusEnum(InternalContainerStatus internal) {
        if( internal == null )
            return null
        return V1ContainerStatus.valueOf(internal.name())
    }

    private static Map<String, V1Vulnerability> toV1Vulnerabilities(Map<String, Integer> internal) {
        if( internal == null )
            return null
        return internal.collectEntries { String severity, Integer count ->
            [(severity): new V1Vulnerability().severity(severity).count(count)]
        } as Map<String, V1Vulnerability>
    }

}

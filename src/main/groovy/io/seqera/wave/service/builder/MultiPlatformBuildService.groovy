/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

package io.seqera.wave.service.builder

import java.time.Instant
import java.util.regex.Pattern

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventPublisher

import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.configuration.BuildEnabled
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.core.ChildRefs
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.service.job.JobHandler
import io.seqera.wave.service.job.JobService
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.scan.ContainerScanService
import io.seqera.wave.tower.PlatformId
import jakarta.annotation.Nullable
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import static io.seqera.wave.util.ContainerHelper.makeContainerId
import static io.seqera.wave.util.ContainerHelper.makeTargetImage
/**
 * Orchestrates multi-platform container builds by fanning out
 * two single-platform builds (linux/amd64 + linux/arm64) and
 * assembling the results into an OCI Image Index.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@Named('MultiBuild')
@CompileStatic
@Requires(bean = BuildEnabled)
class MultiPlatformBuildService implements JobHandler<MultiBuildEntry> {

    @Inject
    ContainerBuildService buildService

    @Inject
    BuildStateStore buildStore

    @Inject
    MultiBuildStateStore multiBuildStore

    @Inject
    ManifestAssembler manifestAssembler

    @Inject
    JobService jobService

    @Inject
    ApplicationEventPublisher<BuildEvent> eventPublisher

    @Inject
    PersistenceService persistenceService

    @Inject @Nullable
    ContainerScanService scanService

    static final ContainerPlatform PLATFORM_AMD64 = ContainerPlatform.of('linux/amd64')
    static final ContainerPlatform PLATFORM_ARM64 = ContainerPlatform.of('linux/arm64')

    /**
     * Build a multi-platform image by orchestrating two single-platform builds
     * and assembling the result into a manifest list.
     *
     * @param templateRequest The original build request used as template
     * @param finalContainerId The container ID for the multi-platform image
     * @param finalTargetImage The final target image tag for the manifest list
     * @param identity The platform identity
     * @return A BuildTrack with the final target image (succeeded=null means in-progress)
     */
    BuildTrack buildMultiPlatformImage(BuildRequest templateRequest, String finalContainerId, String finalTargetImage, PlatformId identity) {
        log.debug "Starting multi-platform build: finalTargetImage=$finalTargetImage, finalContainerId=$finalContainerId"

        // Create platform-specific build requests
        final amd64Request = createPlatformRequest(templateRequest, PLATFORM_AMD64, '-linux-amd64')
        final arm64Request = createPlatformRequest(templateRequest, PLATFORM_ARM64, '-linux-arm64')

        // Submit both platform builds
        final amd64Track = buildService.buildImage(amd64Request)
        final arm64Track = buildService.buildImage(arm64Request)

        log.debug "Submitted platform builds: amd64=${amd64Track.targetImage} (cached=${amd64Track.cached}), arm64=${arm64Track.targetImage} (cached=${arm64Track.cached})"

        final buildId = BuildRequest.ID_PREFIX + finalContainerId + BuildRequest.SEP + '0'
        final startTime = Instant.now()

        // Encode child build IDs for the parent build view
        final buildChildIds = new ChildRefs([
                new ChildRefs.Ref(amd64Track.id, PLATFORM_AMD64.toString()),
                new ChildRefs.Ref(arm64Track.id, PLATFORM_ARM64.toString())
        ])

        // Store an in-progress build entry so status polling can find it
        final syntheticRequest = BuildRequest.of(
                buildId: buildId,
                containerId: finalContainerId,
                targetImage: finalTargetImage,
                startTime: startTime,
                identity: templateRequest.identity,
                containerFile: templateRequest.containerFile,
                condaFile: templateRequest.condaFile,
                workspace: templateRequest.workspace,
                platform: ContainerPlatform.MULTI_PLATFORM,
                configJson: templateRequest.configJson,
                ip: templateRequest.ip,
                offsetId: templateRequest.offsetId,
                scanId: templateRequest.scanId,
                format: templateRequest.format,
                compression: templateRequest.compression,
                buildTemplate: templateRequest.buildTemplate,
                buildChildIds: buildChildIds,
                scanChildIds: templateRequest.scanChildIds
        )
        final initialEntry = BuildEntry.create(syntheticRequest)
        final stored = buildStore.storeIfAbsent(finalTargetImage, initialEntry)
        if( !stored ) {
            // another request already started a build for this image — return the existing build track
            log.debug "Multi-platform build already in progress for $finalTargetImage"
            return new BuildTrack(buildId, finalTargetImage, false, null)
        }

        // Create the multi-build request and entry, then launch via job queue
        final multiBuildRequest = MultiBuildRequest.create(
                finalContainerId,
                finalTargetImage,
                buildId,
                amd64Track.targetImage,
                arm64Track.targetImage,
                amd64Track.cached,
                arm64Track.cached,
                identity,
                templateRequest.maxDuration
        )
        final multiBuildEntry = MultiBuildEntry.of(multiBuildRequest)
        multiBuildStore.put(finalTargetImage, multiBuildEntry)

        // Launch the multi-build job — goes directly to processing queue
        jobService.launchMultiBuild(multiBuildRequest)

        // Return immediately — build is in progress (succeeded=null)
        return new BuildTrack(buildId, finalTargetImage, false, null)
    }

    // **************************************************************
    // **               JobHandler implementation
    // **************************************************************

    @Override
    MultiBuildEntry getJobEntry(JobSpec job) {
        multiBuildStore.get(job.entryKey)
    }

    @Override
    JobSpec launchJob(JobSpec job, MultiBuildEntry entry) {
        // no-op: multi-build jobs don't launch K8s/Docker processes
        return job.withLaunchTime(Instant.now())
    }

    @Override
    void onJobCompletion(JobSpec job, MultiBuildEntry entry, JobState state) {
        final request = entry.request
        final buildId = request.buildId
        final startTime = request.creationTime

        if( state.succeeded() ) {
            try {
                final List<Map> platformEntries = [
                    [image: request.amd64TargetImage, platform: PLATFORM_AMD64],
                    [image: request.arm64TargetImage, platform: PLATFORM_ARM64]
                ] as List<Map>
                manifestAssembler.createAndPushManifestList(request.targetImage, platformEntries, request.identity)
                log.info "Multi-platform manifest list assembled for ${request.targetImage}"

                final completedResult = BuildResult.completed(buildId, 0, 'Multi-platform build completed', startTime, null)
                updateStores(entry, completedResult)
            }
            catch (Exception e) {
                log.error "Multi-platform manifest assembly failed for ${request.targetImage}", e
                final failedResult = BuildResult.failed(buildId, "Manifest assembly failed: ${e.message}", startTime)
                updateStores(entry, failedResult)
            }
        }
        else {
            final failedResult = BuildResult.failed(buildId, state.stdout ?: "Multi-platform build failed", startTime)
            updateStores(entry, failedResult)
        }
    }

    @Override
    void onJobException(JobSpec job, MultiBuildEntry entry, Throwable error) {
        final request = entry.request
        final result = BuildResult.failed(request.buildId, error.message, request.creationTime)
        updateStores(entry, result)
        log.error("Multi-platform build exception for '${request.targetImage}' - operation=${job.operationName}; cause=${error.message}", error)
    }

    @Override
    void onJobTimeout(JobSpec job, MultiBuildEntry entry) {
        final request = entry.request
        final result = BuildResult.failed(request.buildId, "Multi-platform build timed out '${request.targetImage}'", request.creationTime)
        updateStores(entry, result)
        log.warn "Multi-platform build timed out '${request.targetImage}'; operation=${job.operationName}; duration=${result.duration}"
    }

    // **************************************************************
    // **               helper methods
    // **************************************************************

    private void updateStores(MultiBuildEntry entry, BuildResult result) {
        final targetImage = entry.request.targetImage
        // update the multi-build state store
        multiBuildStore.put(targetImage, entry.withResult(result))
        // update the build state store for status polling and publish event
        final existing = buildStore.getBuild(targetImage)
        if( existing ) {
            final updatedEntry = existing.withResult(result)
            buildStore.storeBuild(targetImage, updatedEntry)
            // trigger scan on the composite image before persisting the record
            // (scan record must exist before build record to avoid status polling race condition)
            scanService?.scanOnBuild(updatedEntry)
            // persist the build record and publish event (triggers email notification)
            final event = new BuildEvent(updatedEntry.request, result)
            persistenceService.saveBuildAsync(WaveBuildRecord.fromEvent(event))
            eventPublisher.publishEvent(event)
        }
        else {
            log.warn "Multi-platform build entry not found in build store for $targetImage"
        }
    }

    static final private Pattern FUSION_LAYER_ARCH = Pattern.compile('.*/fusion-(\\w+)\\.tar\\.gz$')

    /**
     * Filter container config layers to only include fusion layers matching the target platform arch.
     * Non-fusion layers are kept as-is.
     */
    static ContainerConfig filterLayersForPlatform(ContainerConfig config, ContainerPlatform platform) {
        if( !config?.layers || !platform || platform.isMultiArch() )
            return config
        final filtered = config.layers.findAll { ContainerLayer layer ->
            final matcher = layer.location ? FUSION_LAYER_ARCH.matcher(layer.location) : null
            final keep = !matcher?.matches() || matcher.group(1) == platform.arch
            if( matcher?.matches() ) {
                log.debug "Layer filter: platform=${platform}; layer=${layer.location}; arch=${matcher.group(1)}; keep=${keep}"
            }
            return keep
        }
        log.debug "Layer filter: platform=${platform}; input=${config.layers.size()} layers; output=${filtered.size()} layers"
        return new ContainerConfig(config.entrypoint, config.cmd, config.env, config.workingDir, filtered)
    }

    protected BuildRequest createPlatformRequest(BuildRequest template, ContainerPlatform platform, String suffix) {
        final repo = ContainerCoordinates.parse(template.targetImage).repository
        final containerConfig = filterLayersForPlatform(template.containerConfig, platform)
        final platformId = makeContainerId(
                template.containerFile,
                template.condaFile,
                platform,
                repo,
                template.buildContext,
                containerConfig
        )
        final platformImage = makeTargetImage(template.format, repo, platformId, template.condaFile, null)

        return new BuildRequest(
                platformId,
                template.containerFile,
                template.condaFile,
                template.workspace,
                platformImage,
                template.identity,
                platform,
                template.cacheRepository,
                template.ip,
                template.configJson,
                template.offsetId,
                containerConfig,
                null,       // scanId - suppress per-platform scans; scan runs on the composite image
                template.buildContext,
                template.format,
                template.maxDuration,
                template.compression,
                template.buildTemplate,
                true    // noEmail - suppress individual sub-build notifications
        )
    }
}

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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.job.JobEntry
import io.seqera.wave.service.job.JobHandler
import io.seqera.wave.service.job.JobService
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.tower.PlatformId
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

        // Store an in-progress build entry so status polling can find it
        final syntheticRequest = BuildRequest.of(
                buildId: buildId,
                containerId: finalContainerId,
                targetImage: finalTargetImage,
                startTime: startTime,
                identity: templateRequest.identity,
                containerFile: templateRequest.containerFile,
                condaFile: templateRequest.condaFile,
                platform: templateRequest.platform,
                ip: templateRequest.ip,
                offsetId: templateRequest.offsetId,
                scanId: templateRequest.scanId,
                format: templateRequest.format,
                compression: templateRequest.compression,
                buildTemplate: templateRequest.buildTemplate
        )
        final initialEntry = BuildEntry.create(syntheticRequest)
        buildStore.storeIfAbsent(finalTargetImage, initialEntry)

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
                ]
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
        // update the build state store for status polling
        final existing = buildStore.getBuild(targetImage)
        if( existing ) {
            buildStore.storeBuild(targetImage, existing.withResult(result))
        }
        else {
            log.warn "Multi-platform build entry not found in build store for $targetImage"
        }
    }

    protected BuildRequest createPlatformRequest(BuildRequest template, ContainerPlatform platform, String suffix) {
        final repo = template.targetImage.split(':')[0]
        final platformId = makeContainerId(
                template.containerFile,
                template.condaFile,
                platform,
                repo,
                template.buildContext,
                template.containerConfig
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
                template.containerConfig,
                template.scanId,
                template.buildContext,
                template.format,
                template.maxDuration,
                template.compression,
                template.buildTemplate
        )
    }
}

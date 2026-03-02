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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.core.ContainerPlatform
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
@CompileStatic
class MultiPlatformBuildService {

    @Inject
    ContainerBuildService buildService

    @Inject
    BuildStateStore buildStore

    @Inject
    ManifestAssembler manifestAssembler

    @Inject
    @Named(TaskExecutors.BLOCKING)
    ExecutorService executor

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

        // Async: wait for both builds to complete, then assemble manifest list
        CompletableFuture.runAsync({
            try {
                awaitAndAssemble(amd64Track, arm64Track, finalTargetImage, buildId, startTime, identity)
            }
            catch (Exception e) {
                log.error "Multi-platform build failed for $finalTargetImage", e
                // Store failure so status polling sees completion
                final failedResult = BuildResult.failed(buildId, e.message, startTime)
                storeBuildResult(finalTargetImage, buildId, failedResult)
            }
        }, executor)

        // Return immediately — build is in progress (succeeded=null)
        return new BuildTrack(buildId, finalTargetImage, false, null)
    }

    protected void awaitAndAssemble(BuildTrack amd64Track, BuildTrack arm64Track, String finalTargetImage, String buildId, Instant startTime, PlatformId identity) {
        // await both platform builds in parallel
        final amd64Future = amd64Track.cached
                ? CompletableFuture.completedFuture(true)
                : CompletableFuture.supplyAsync({ awaitBuildResult(amd64Track, 'amd64', finalTargetImage) }, executor)
        final arm64Future = arm64Track.cached
                ? CompletableFuture.completedFuture(true)
                : CompletableFuture.supplyAsync({ awaitBuildResult(arm64Track, 'arm64', finalTargetImage) }, executor)

        CompletableFuture.allOf(amd64Future, arm64Future).join()
        final boolean amd64Ok = amd64Future.get()
        final boolean arm64Ok = arm64Future.get()

        log.debug "Platform build results: amd64=$amd64Ok, arm64=$arm64Ok"

        if( amd64Ok && arm64Ok ) {
            final List<Map> platformEntries = [
                [image: amd64Track.targetImage, platform: PLATFORM_AMD64],
                [image: arm64Track.targetImage, platform: PLATFORM_ARM64]
            ]
            manifestAssembler.createAndPushManifestList(finalTargetImage, platformEntries, identity)
            log.info "Multi-platform manifest list assembled for $finalTargetImage"
            // store completed build result
            final completedResult = BuildResult.completed(buildId, 0, 'Multi-platform build completed', startTime, null)
            storeBuildResult(finalTargetImage, buildId, completedResult)
        }
        else {
            log.error "Multi-platform build failed — amd64Ok=$amd64Ok, arm64Ok=$arm64Ok"
            final failedResult = BuildResult.failed(buildId, "Multi-platform build failed — amd64=$amd64Ok, arm64=$arm64Ok", startTime)
            storeBuildResult(finalTargetImage, buildId, failedResult)
        }
    }

    private void storeBuildResult(String targetImage, String buildId, BuildResult result) {
        final existing = buildStore.getBuild(targetImage)
        if( existing ) {
            buildStore.storeBuild(targetImage, existing.withResult(result))
        }
        else {
            log.warn "Multi-platform build entry not found for $targetImage, buildId=$buildId"
        }
    }

    private boolean awaitBuildResult(BuildTrack track, String arch, String finalTargetImage) {
        final future = buildStore.awaitBuild(track.targetImage)
        if( !future ) {
            log.error "Multi-platform build: unable to await $arch build for $finalTargetImage"
            return false
        }
        return future.get()?.succeeded()
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

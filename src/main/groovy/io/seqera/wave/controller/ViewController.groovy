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

package io.seqera.wave.controller

import java.util.regex.Pattern

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.views.View
import io.seqera.wave.exception.NotFoundException
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.service.logs.BuildLogService
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.scan.ScanResult
import io.seqera.wave.util.JacksonHelper
import jakarta.inject.Inject
import static io.seqera.wave.util.DataTimeUtils.formatDuration
import static io.seqera.wave.util.DataTimeUtils.formatTimestamp
/**
 * Implements View controller
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Controller("/view")
@ExecuteOn(TaskExecutors.IO)
class ViewController {

    @Inject
    @Value('${wave.server.url}')
    private String serverUrl

    @Inject
    private PersistenceService persistenceService

    @Inject
    private ContainerBuildService buildService

    @Inject
    @Nullable
    private BuildLogService buildLogService

    @Inject
    private ContainerInspectService inspectService

    @View("build-view")
    @Get('/builds/{buildId}')
    HttpResponse viewBuild(String buildId) {
        // check redirection for invalid suffix in the form `-nn`
        final r1 = shouldRedirect1(buildId)
        if( r1 ) {
            log.debug "Redirect to build page: $r1"
            return HttpResponse.redirect(URI.create(r1))
        }
        // check redirection when missing the suffix `_nn`
        final r2 = shouldRedirect2(buildId)
        if( r2 ) {
            log.debug "Redirect to build page: $r2"
            return HttpResponse.redirect(URI.create(r2))
        }
        // go ahead with proper handling
        final record = buildService.getBuildRecord(buildId)
        if( !record )
            throw new NotFoundException("Unknown build id '$buildId'")
        log.debug "View build page for: $buildId"
        return HttpResponse.ok(renderBuildView(record))
    }

    static final private Pattern DASH_SUFFIX = ~/([0-9a-zA-Z\-]+)-(\d+)$/

    static final private Pattern MISSING_SUFFIX = ~/([0-9a-zA-Z\-]+)(?<!_\d{2})$/

    protected String shouldRedirect1(String buildId) {
        // check for build id containing a -nn suffix
        final check1 = DASH_SUFFIX.matcher(buildId)
        if( check1.matches() ) {
            return "/view/builds/${check1.group(1)}_${check1.group(2)}"
        }
        return null
    }

    protected String shouldRedirect2(String buildId) {
        // check build id missing the _nn suffix
        if( !MISSING_SUFFIX.matcher(buildId).matches() )
            return null

        final rec = buildService.getLatestBuild(buildId)
        if( !rec || !rec.buildId.startsWith(buildId) )
            return null

        return "/view/builds/${rec.buildId}"
    }

    Map<String,String> renderBuildView(WaveBuildRecord result) {
        // create template binding
        final binding = new HashMap(20)
        binding.build_id = result.buildId
        binding.build_success = result.succeeded()
        binding.build_failed = result.exitStatus  && result.exitStatus != 0
        binding.build_in_progress = result.exitStatus == null
        binding.build_exit_status = result.exitStatus
        binding.build_user = (result.userName ?: '-') + " (ip: ${result.requestIp})"
        binding.build_time = formatTimestamp(result.startTime, result.offsetId) ?: '-'
        binding.build_duration = formatDuration(result.duration) ?: '-'
        binding.build_image = result.targetImage
        binding.build_format = result.format?.render() ?: 'Docker'
        binding.build_platform = result.platform
        binding.build_containerfile = result.dockerFile ?: '-'
        binding.build_condafile = result.condaFile
        binding.build_digest = result.digest ?: '-'
        binding.put('server_url', serverUrl)
        binding.scan_url = result.scanId && result.succeeded() ? "$serverUrl/view/scans/${result.scanId}" : null
        binding.scan_id = result.scanId
        // configure build logs when available
        if( buildLogService ) {
            final buildLog = buildLogService.fetchLogString(result.buildId)
            binding.build_log_data = buildLog?.data
            binding.build_log_truncated = buildLog?.truncated
            binding.build_log_url = "$serverUrl/v1alpha1/builds/${result.buildId}/logs"
        }
        // result the main object
        return binding
      }

    @View("container-view")
    @Get('/containers/{token}')
    HttpResponse<Map<String,Object>> viewContainer(String token) {
        final data = persistenceService.loadContainerRequest(token)
        if( !data )
            throw new NotFoundException("Unknown container token: $token")
        // return the response
        final binding = new HashMap(20)
        binding.request_token = token
        binding.request_container_image = data.containerImage
        binding.request_contaiener_platform = data.platform ?: '-'
        binding.request_fingerprint = data.fingerprint ?: '-'
        binding.request_timestamp = formatTimestamp(data.timestamp, data.zoneId) ?: '-'
        binding.request_expiration = formatTimestamp(data.expiration)
        binding.request_container_config = data.containerConfig

        binding.source_container_image = data.sourceImage ?: '-'
        binding.source_container_digest = data.sourceDigest ?: '-'

        binding.wave_container_image = data.waveImage ?: '-'
        binding.wave_container_digest = data.waveDigest ?: '-'

        // user & tower data
        binding.tower_user_id = data.user?.id
        binding.tower_user_email = data.user?.email
        binding.tower_user_name = data.user?.userName
        binding.tower_workspace_id = data.workspaceId ?: '-'
        binding.tower_endpoint = data.towerEndpoint

        binding.build_container_file = data.containerFile
        binding.build_conda_file = data.condaFile ?: '-'
        binding.build_repository = data.buildRepository ?: '-'
        binding.build_cache_repository = data.cacheRepository  ?: '-'
        binding.build_id = data.buildId ?: '-'
        binding.build_cached = data.buildId ? !data.buildNew : '-'
        binding.build_freeze = data.buildId ? data.freeze : '-'
        binding.build_url = data.buildId ? "$serverUrl/view/builds/${data.buildId}" : '#'
        binding.fusion_version = data.fusionVersion ?: '-'

        return HttpResponse.<Map<String,Object>>ok(binding)
    }

    @View("scan-view")
    @Get('/scans/{scanId}')
    HttpResponse<Map<String,Object>> viewScan(String scanId) {
        final binding = new HashMap(10)
        try {
            final result = persistenceService.loadScanResult(scanId)
            makeScanViewBinding(result, binding)
        }
        catch (NotFoundException e){
            binding.scan_exist = false
            binding.scan_completed = true
            binding.error_message = e.getMessage()
            binding.should_refresh = false
        }

        // return the response
        binding.put('server_url', serverUrl)
        return HttpResponse.<Map<String,Object>>ok(binding)
    }

    @View("inspect-view")
    @Get('/inspect')
    HttpResponse<Map<String,Object>> viewInspect(@QueryValue String image) {
        final binding = new HashMap(10)
        try {
            final spec = inspectService.containerSpec(image, null)
            binding.imageName = spec.imageName
            binding.reference = spec.reference
            binding.digest = spec.digest
            binding.registry = spec.registry
            binding.hostName = spec.hostName
            binding.config = JacksonHelper.toJson(spec.config)
            binding.manifest = JacksonHelper.toJson(spec.manifest)
        }catch (Exception e){
            binding.error_message = e.getMessage()
        }

        // return the response
        binding.put('server_url', serverUrl)
        return HttpResponse.<Map<String,Object>>ok(binding)
    }

    Map<String, Object> makeScanViewBinding(ScanResult result, Map<String,Object> binding=new HashMap(10)) {
        binding.should_refresh = !result.isCompleted()
        binding.scan_id = result.id
        binding.scan_container_image = result.containerImage ?: '-'
        binding.scan_exist = true
        binding.scan_completed = result.isCompleted()
        binding.scan_status = result.status
        binding.scan_failed = result.status == ScanResult.FAILED
        binding.scan_succeeded = result.status == ScanResult.SUCCEEDED
        binding.build_id = result.buildId
        binding.build_url = "$serverUrl/view/builds/${result.buildId}"
        binding.scan_time = formatTimestamp(result.startTime) ?: '-'
        binding.scan_duration = formatDuration(result.duration) ?: '-'
        if ( result.vulnerabilities )
            binding.vulnerabilities = result.vulnerabilities.toSorted().reverse()

        return binding
    }

}

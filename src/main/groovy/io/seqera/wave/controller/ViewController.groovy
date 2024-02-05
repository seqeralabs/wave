/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

import io.micronaut.core.annotation.Nullable

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.views.View
import io.seqera.wave.exception.NotFoundException
import io.seqera.wave.service.logs.BuildLogService
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.scan.ScanResult
import jakarta.inject.Inject
import static io.seqera.wave.util.DataTimeUtils.formatDuration
import static io.seqera.wave.util.DataTimeUtils.formatTimestamp
/**
 * Implements View controller
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
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
    @Nullable
    private BuildLogService buildLogService

    @View("build-view")
    @Get('/builds/{buildId}')
    HttpResponse<Map<String,String>> viewBuild(String buildId) {
        final record = persistenceService.loadBuild(buildId)
        if( !record )
            throw new NotFoundException("Unknown build id '$buildId'")
        return HttpResponse.ok(renderBuildView(record))
    }

    Map<String,String> renderBuildView(WaveBuildRecord result) {
        // create template binding
        final binding = new HashMap(20)
        binding.build_id = result.buildId
        binding.build_success = result.succeeded()
        binding.build_exit_status = result.exitStatus
        binding.build_user = (result.userName ?: '-') + " (ip: ${result.requestIp})"
        binding.build_time = formatTimestamp(result.startTime, result.offsetId) ?: '-'
        binding.build_duration = formatDuration(result.duration) ?: '-'
        binding.build_image = result.targetImage
        binding.build_format = result.format?.render() ?: 'Docker'
        binding.build_platform = result.platform
        binding.build_containerfile = result.dockerFile ?: '-'
        binding.build_condafile = result.condaFile
        binding.build_spackfile = result.spackFile
        binding.build_spacktarget = result.spackTarget
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


        return HttpResponse.<Map<String,Object>>ok(binding)
    }

    @View("scan-view")
    @Get('/scans/{scanId}')
    HttpResponse<Map<String,Object>> viewScan(String scanId) {
        final binding = new HashMap(10)
        try {
            final result = persistenceService.loadScanResult(scanId)
            binding.should_refresh = !result.isCompleted()
            binding.scan_id = result.id
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

}

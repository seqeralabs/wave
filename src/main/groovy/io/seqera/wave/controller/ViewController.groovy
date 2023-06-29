package io.seqera.wave.controller

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.views.View
import io.seqera.wave.exception.NotFoundException
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
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
class ViewController {

    @Inject
    @Value('${wave.server.url}')
    private String serverUrl

    @Inject
    private PersistenceService persistenceService

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
        binding.build_success = result.exitStatus==0
        binding.build_exit_status = result.exitStatus
        binding.build_user = (result.userName ?: '-') + " (ip: ${result.requestIp})"
        binding.build_time = formatTimestamp(result.startTime, result.offsetId) ?: '-'
        binding.build_duration = formatDuration(result.duration) ?: '-'
        binding.build_image = result.targetImage
        binding.build_platform = result.platform
        binding.build_dockerfile = result.dockerFile ?: '-'
        binding.build_condafile = result.condaFile
        binding.build_spackfile = result.spackFile
        binding.put('server_url', serverUrl)
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
    @Get('/scans/{buildId}')
    HttpResponse<Map<String,Object>> viewScan(String buildId) {
        final record = persistenceService.loadContainerScanResult(buildId)
        if( !record )
            throw new NotFoundException("Unknown build id '$buildId'")
        // return the response
        final binding = new HashMap(5)
        binding.request_scan_result = record.scanResult
        return HttpResponse.<Map<String,Object>>ok(binding)
    }
}

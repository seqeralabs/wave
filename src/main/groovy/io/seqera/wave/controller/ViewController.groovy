package io.seqera.wave.controller

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.seqera.wave.exception.NotFoundException
import io.seqera.wave.mail.MailHelper
import io.seqera.wave.service.persistence.BuildRecord
import io.seqera.wave.service.persistence.PersistenceService
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

    @Produces(MediaType.TEXT_HTML)
    @Get('/builds/{buildId}')
    String builds(String buildId) {
        final record = persistenceService.loadBuild(buildId)
        if( !record )
            throw new NotFoundException("Unknown build id '$buildId'")
        return renderBuildView(record)
    }

    String renderBuildView(BuildRecord result) {
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
        binding.put('server_url', serverUrl)
        // result the main object
        MailHelper.getTemplateFile('/io/seqera/wave/build-view.html', binding)
      }
}

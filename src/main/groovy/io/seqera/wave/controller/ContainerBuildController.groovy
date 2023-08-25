package io.seqera.wave.controller

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import jakarta.inject.Inject

/**
 * Implements a controller for container builds
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Controller("/")
@ExecuteOn(TaskExecutors.IO)
class ContainerBuildController {

    @Inject
    private PersistenceService persistenceService

    @Get("/v1alpha1/builds/{buildId}")
    HttpResponse<WaveBuildRecord> getBuildRecord(String buildId){
        final record = persistenceService.loadBuild(buildId)
        return record
                ? HttpResponse.ok(record)
                : HttpResponse.<WaveBuildRecord>notFound()
    }

}

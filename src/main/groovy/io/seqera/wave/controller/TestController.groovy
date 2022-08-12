package io.seqera.wave.controller

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.seqera.wave.service.builder.ContainerBuildService
import jakarta.inject.Inject
/**
 * Just for testing
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Controller("/")
class TestController {

    @Inject
    ContainerBuildService builderService

    @Get('/test-build')
    HttpResponse<String> testBuild() {
        final dockerFile = """\
            FROM quay.io/nextflow/bash
            RUN echo "Look ma' building 🐳🐳 on the fly!" > /hello.txt
            ENV NOW=${System.currentTimeMillis()}
            """
        final resp = builderService.buildImage(dockerFile, null, null)
        return HttpResponse.ok(resp)
    }

}

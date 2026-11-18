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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.HttpHeaders
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.CookieValue
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Produces
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.configuration.BuildEnabled
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.logs.BuildLogService
import io.seqera.wave.service.view.auth.ViewAuthService
import jakarta.inject.Inject
/**
 * Implements a controller for container builds
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Requires(bean = BuildEnabled)
@Controller("/")
@ExecuteOn(TaskExecutors.BLOCKING)
class BuildController {

    @Inject
    private ContainerBuildService buildService

    @Inject
    private RecordAccessGuard guard

    @Inject
    @Nullable
    private BuildLogService logService

    @Get("/v1alpha1/builds/{buildId}")
    HttpResponse<?> getBuildRecord(String buildId, @Nullable @Header(HttpHeaders.AUTHORIZATION) String authorization, @Nullable @CookieValue(ViewAuthService.COOKIE) String sessionId) {
        final record = buildService.getBuildRecord(buildId)
        if( !record )
            return HttpResponse.notFound()
        return guard.refuse(record.owner, record.visibility, authorization, sessionId) ?: HttpResponse.ok(record)
    }

    @Produces(MediaType.TEXT_PLAIN)
    @Get(value="/v1alpha1/builds/{buildId}/logs")
    HttpResponse<?> getBuildLog(String buildId, @Nullable @Header(HttpHeaders.AUTHORIZATION) String authorization, @Nullable @CookieValue(ViewAuthService.COOKIE) String sessionId){
        if( logService==null )
            throw new IllegalStateException("Build Logs service not configured")
        final denied = refuseBuild(buildId, authorization, sessionId)
        if( denied )
            return denied
        final logs = logService.fetchLogStream(buildId)
        return logs ? HttpResponse.ok(logs) : HttpResponse.notFound()
    }

    @Get("/v1alpha1/builds/{buildId}/status")
    HttpResponse<?> getBuildStatus(String buildId, @Nullable @Header(HttpHeaders.AUTHORIZATION) String authorization, @Nullable @CookieValue(ViewAuthService.COOKIE) String sessionId) {
        final build = buildService.getBuildRecord(buildId)
        if( build == null )
            return HttpResponse.notFound()
        return guard.refuse(build.owner, build.visibility, authorization, sessionId) ?: HttpResponse.ok(build.toStatusResponse())
    }

    @Produces(MediaType.TEXT_PLAIN)
    @Get(value="/v1alpha1/builds/{buildId}/condalock")
    HttpResponse<?> getCondaLock(String buildId, @Nullable @Header(HttpHeaders.AUTHORIZATION) String authorization, @Nullable @CookieValue(ViewAuthService.COOKIE) String sessionId){
        if( logService==null )
            throw new IllegalStateException("Build Logs service not configured")
        final denied = refuseBuild(buildId, authorization, sessionId)
        if( denied )
            return denied
        final condaLock = logService.fetchCondaLockStream(buildId)
        return condaLock ? HttpResponse.ok(condaLock) : HttpResponse.notFound()
    }

    /** the artifact routes have no record of their own: they are guarded by the build that owns them */
    private HttpResponse<?> refuseBuild(String buildId, String authorization, String sessionId) {
        // a record that has aged out leaves the artifact unguarded, as it was before: refusing
        // here would 404 a route released clients can still read today
        final record = buildService.getBuildRecord(buildId)
        return record ? guard.refuse(record.owner, record.visibility, authorization, sessionId) : null
    }

}

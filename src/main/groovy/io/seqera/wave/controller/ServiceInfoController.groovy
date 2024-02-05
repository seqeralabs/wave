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

import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.seqera.wave.api.ServiceInfo
import io.seqera.wave.api.ServiceInfoResponse
import io.seqera.wave.util.BuildInfo
/**
 * Basic service info endpoint
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Controller("/")
@Secured(SecurityRule.IS_ANONYMOUS)
@ExecuteOn(TaskExecutors.IO)
class ServiceInfoController {

    @Value('${wave.landing.url}')
    @Nullable
    String landingUrl

    @Get('/service-info')
    HttpResponse<ServiceInfoResponse> info() {
        final info = new ServiceInfo(BuildInfo.getVersion(), BuildInfo.getCommitId())
        HttpResponse.ok(new ServiceInfoResponse(info))
    }

    @Get('/ping')
    HttpResponse<ServiceInfoResponse> ping() {
        HttpResponse.ok()
    }

    @Get("/")
    HttpResponse landing() {
        return landingUrl
                ? HttpResponse.redirect(new URI(landingUrl))
                : HttpResponse.badRequest()
    }

}

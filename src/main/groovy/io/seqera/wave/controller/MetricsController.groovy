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

import javax.annotation.Nullable

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.AuthorizationException
import io.micronaut.security.rules.SecurityRule
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.metric.MetricsService
import io.seqera.wave.service.metric.model.GetBuildsCountResponse
import io.seqera.wave.service.metric.model.GetFusionPullsCountResponse
import io.seqera.wave.service.metric.model.GetPullsCountResponse
import jakarta.inject.Inject

import static io.micronaut.http.HttpHeaders.WWW_AUTHENTICATE
/**
 * Controller for wave Metrics
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@CompileStatic
@Requires(property = 'wave.metrics.enabled', value = 'true')
@Secured(SecurityRule.IS_AUTHENTICATED)
@Controller
class MetricsController {

    @Inject
    private MetricsService metricsService

    @Get(uri = "/v1alpha2/metrics/builds", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getBuildsMetrics(@Nullable @QueryValue String date, @Nullable @QueryValue String org) {
        validateQueryParams(date, org)
        final count = metricsService.getBuildsMetrics(date, org)
        return HttpResponse.ok(new GetBuildsCountResponse(count))
    }

    @Get(uri = "/v1alpha2/metrics/pulls", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getPullsMetrics(@Nullable @QueryValue String date, @Nullable @QueryValue String org) {
        validateQueryParams(date, org)
        final count = metricsService.getPullsMetrics(date, org)
        return HttpResponse.ok(new GetPullsCountResponse(count))
    }

    @Get(uri = "/v1alpha2/metrics/fusion/pulls", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getFusionPullsMetrics(@Nullable @QueryValue String date, @Nullable @QueryValue String org) {
        validateQueryParams(date, org)
        final count = metricsService.getFusionPullsMetrics(date, org)
        return HttpResponse.ok(new GetFusionPullsCountResponse(count))

    }

    @Error(exception = AuthorizationException.class)
    HttpResponse<?> handleAuthorizationException() {
        return HttpResponse.unauthorized()
                .header(WWW_AUTHENTICATE, "Basic realm=Wave Authentication")
    }

    static void validateQueryParams(String date, String org) {
        if(!date && !org)
            throw new BadRequestException('Either date or org query parameter must be provided')
        def pattern = ~/\d{4}-\d{2}-\d{2}/
        if(date && !(date ==~ pattern)){
            throw new BadRequestException('date format should be yyyy-MM-dd')
        }
    }
}

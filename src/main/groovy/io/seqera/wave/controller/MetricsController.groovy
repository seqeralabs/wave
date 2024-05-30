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
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.AuthorizationException
import io.micronaut.security.rules.SecurityRule
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.metric.MetricConstants
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
@ExecuteOn(TaskExecutors.IO)
class MetricsController {

    @Inject
    private MetricsService metricsService

    static final private Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    @Get(uri = "/v1alpha2/metrics/builds", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getBuildsMetrics(@Nullable @QueryValue String date, @Nullable @QueryValue String org) {
        if(!date && !org)
            return HttpResponse.ok(metricsService.getOrgCount(MetricConstants.PREFIX_BUILDS))
        validateQueryParams(date)
        return HttpResponse.ok(metricsService.getOrgCountPerDate(MetricConstants.PREFIX_BUILDS, date, org))
    }

    @Get(uri = "/v1alpha2/metrics/pulls", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getPullsMetrics(@Nullable @QueryValue String date, @Nullable @QueryValue String org) {
        if(!date && !org)
            return HttpResponse.ok(metricsService.getOrgCount(MetricConstants.PREFIX_PULLS))
        validateQueryParams(date)
        return HttpResponse.ok(metricsService.getOrgCountPerDate(MetricConstants.PREFIX_PULLS, date, org))
    }

    @Get(uri = "/v1alpha2/metrics/fusion/pulls", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getFusionPullsMetrics(@Nullable @QueryValue String date, @Nullable @QueryValue String org) {
        if(!date && !org)
            return HttpResponse.ok(metricsService.getOrgCount(MetricConstants.PREFIX_FUSION))
        validateQueryParams(date)
        return HttpResponse.ok(metricsService.getOrgCountPerDate(MetricConstants.PREFIX_FUSION, date, org))

    }

    @Error(exception = AuthorizationException.class)
    HttpResponse<?> handleAuthorizationException() {
        return HttpResponse.unauthorized()
                .header(WWW_AUTHENTICATE, "Basic realm=Wave Authentication")
    }

    static void validateQueryParams(String date) {
        if(date && !DATE_PATTERN.matcher(date).matches()) {
            throw new BadRequestException('date format should be yyyy-MM-dd')
        }
    }
}

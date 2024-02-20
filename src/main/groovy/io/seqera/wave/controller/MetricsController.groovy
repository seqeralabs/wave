/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import javax.annotation.Nullable

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.AuthorizationException
import io.micronaut.security.rules.SecurityRule
import io.seqera.wave.service.metric.Metric
import io.seqera.wave.service.metric.MetricFilter
import io.seqera.wave.service.metric.MetricService
import io.seqera.wave.service.metric.model.GetBuildsMetricsResponse
import io.seqera.wave.service.metric.model.GetBuildsCountResponse
import io.seqera.wave.service.metric.model.GetDistinctCountResponse
import io.seqera.wave.service.metric.model.GetPullsCountResponse
import io.seqera.wave.service.metric.model.GetPullsMetricsResponse
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
@Controller("/v1alpha1/metrics")
class MetricsController {
    @Inject
    private MetricService metricsService

    @Get(uri = "/builds/{metric}", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getBuildMetrics(@PathVariable String metric,
                                      @Nullable @QueryValue Boolean success,
                                      @Nullable @QueryValue String startDate,
                                      @Nullable @QueryValue String endDate,
                                      @Nullable @QueryValue Integer limit) {
        final result = metricsService.getBuildMetrics(
                        Metric.valueOf(metric),
                        new MetricFilter.Builder()
                                .dates(parseStartDate(startDate), parseEndDate(endDate))
                                .success(success)
                                .limit(limit)
                                .build())
        return HttpResponse.ok(new GetBuildsMetricsResponse(result))
    }

    @Get(uri = "/builds", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getBuildsCount(@Nullable @QueryValue Boolean success,
                                               @Nullable @QueryValue String startDate,
                                               @Nullable @QueryValue String endDate) {
        final count = metricsService.getBuildCount(
                        new MetricFilter.Builder()
                                .dates(parseStartDate(startDate), parseEndDate(endDate))
                                .success(success)
                                .build())
        return HttpResponse.ok(new GetBuildsCountResponse(count))
    }

    @Get(uri = "/pulls/{metric}", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getPullsMetrics(@PathVariable String metric,
                                      @Nullable @QueryValue String startDate,
                                      @Nullable @QueryValue String endDate,
                                      @Nullable @QueryValue Boolean fusion,
                                      @Nullable @QueryValue Integer limit) {
        final result = metricsService.getPullMetrics(
                        Metric.valueOf(metric),
                        new MetricFilter.Builder()
                                .dates(parseStartDate(startDate), parseEndDate(endDate))
                                .fusion(fusion)
                                .limit(limit)
                                .build())
        return HttpResponse.ok(new GetPullsMetricsResponse(result))

    }

    @Get(uri = "/pulls", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getPullsCount(@Nullable @QueryValue String startDate,
                                              @Nullable @QueryValue String endDate,
                                              @Nullable @QueryValue Boolean fusion) {
        final count = metricsService.getPullCount(
                        new MetricFilter.Builder()
                                .dates(parseStartDate(startDate), parseEndDate(endDate))
                                .fusion(fusion)
                                .build())
        return HttpResponse.ok(new GetPullsCountResponse(count))
    }

    @Get(uri = "/distinct/{metric}", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getBuildsCount(@PathVariable String metric,
                                               @Nullable @QueryValue String startDate,
                                               @Nullable @QueryValue String endDate,
                                               @Nullable @QueryValue Boolean fusion) {
        final count = metricsService.getDistinctMetrics(
                        Metric.valueOf(metric),
                        new MetricFilter.Builder()
                                .dates(parseStartDate(startDate), parseEndDate(endDate))
                                .fusion(fusion)
                                .build())
        return HttpResponse.ok(new GetDistinctCountResponse(count))
    }

    static Instant parseStartDate(String date) {
        if (!date)
            return null
        LocalDate localDate = LocalDate.parse(date)
        return localDate.atTime(LocalTime.MIN).atZone(ZoneId.systemDefault()).toInstant()
    }

    static Instant parseEndDate(String date) {
        if (!date)
            return null
        LocalDate localDate = LocalDate.parse(date)
        return localDate.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant()
    }

    @Error(exception = DateTimeParseException.class)
    HttpResponse<?> handleDateTimeParseException() {
        return HttpResponse.badRequest([message: 'Date format should be yyyy-mm-dd'])
    }

    @Error(exception = AuthorizationException.class)
    HttpResponse<?> handleAuthorizationException() {
        return HttpResponse.unauthorized()
                .header(WWW_AUTHENTICATE, "Basic realm=Wave Authentication")
    }

    @Error(exception = IllegalArgumentException.class)
    HttpResponse<?> handleIllegalArgumentException() {
        return HttpResponse.badRequest([message: 'you have provided an invalid metric. ' +
                'The valid metrics are: ' + Metric.values().collect({ it.name() }).join(', ')])
    }
}

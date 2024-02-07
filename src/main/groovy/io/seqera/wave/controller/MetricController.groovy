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
import java.time.ZoneId
import java.time.format.DateTimeParseException
import javax.annotation.Nullable

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.seqera.wave.service.metric.Metric
import io.seqera.wave.service.metric.MetricService
import jakarta.inject.Inject

/**
 * Controller for wave Metrics
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@CompileStatic
@Secured(SecurityRule.IS_AUTHENTICATED)
@Controller("/v1/metrics")
class MetricController {
    @Inject
    private MetricService metricsService

    @Get(uri="/pull/{metric}", produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map> getPullMetrics(@PathVariable String metric, @Nullable @QueryValue String startDate, @Nullable @QueryValue String endDate) {
        try {
            def result = metricsService.getPullMetrics(Metric.valueOf(metric), parseDate(startDate), parseDate(endDate))
            if( result && result.size() > 0)
                return HttpResponse.ok(result)
            else
                return HttpResponse.notFound()
        }catch (IllegalArgumentException | DateTimeParseException e) {
            return HttpResponse.badRequest((Map)[message: e.message])
        }
    }

    @Get(uri="/build/{metric}", produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map> getBuildMetrics(@PathVariable String metric, @Nullable @QueryValue Boolean success, @Nullable @QueryValue String startDate, @Nullable @QueryValue String endDate) {
        try {
            def result = metricsService.getBuildMetrics(Metric.valueOf(metric), success, parseDate(startDate), parseDate(endDate))
            if( result && result.size() > 0)
                return HttpResponse.ok(result)
            else
                return HttpResponse.notFound()
        }catch (IllegalArgumentException | DateTimeParseException e) {
            return HttpResponse.badRequest((Map)[message: e.message])
        }
    }

    @Get(uri="/pull/count", produces = MediaType.APPLICATION_JSON)
    HttpResponse<LinkedHashMap> getPullCount(@Nullable @QueryValue String startDate, @Nullable @QueryValue String endDate) {
        try {
            return HttpResponse.ok([count: metricsService.getPullCount(parseDate(startDate), parseDate(endDate))])
        }catch (IllegalArgumentException | DateTimeParseException e) {
            return HttpResponse.badRequest([message: e.message])
        }
    }

    @Get(uri="/build/count", produces = MediaType.APPLICATION_JSON)
    HttpResponse<LinkedHashMap> getBuildCount(@Nullable @QueryValue Boolean success, @Nullable @QueryValue String startDate, @Nullable @QueryValue String endDate) {
        try {
            return HttpResponse.ok([count: metricsService.getBuildCount(success, parseDate(startDate), parseDate(endDate))])
        }catch (IllegalArgumentException | DateTimeParseException e) {
            return HttpResponse.badRequest([message: e.message])
        }
    }

    @Get(uri="/distinct/{metric}", produces = MediaType.APPLICATION_JSON)
    HttpResponse<LinkedHashMap> getBuildCount(@PathVariable String metric, @Nullable @QueryValue String startDate, @Nullable @QueryValue String endDate) {
        try {
            return HttpResponse.ok([count: metricsService.getDistinctMetrics(Metric.valueOf(metric), parseDate(startDate), parseDate(endDate))])
        }catch (IllegalArgumentException | DateTimeParseException e) {
            return HttpResponse.badRequest([message: e.message])
        }
    }

    protected Instant parseDate(String date) {
        if( !date )
            return null
        LocalDate localDate = LocalDate.parse(date);
        return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }
}

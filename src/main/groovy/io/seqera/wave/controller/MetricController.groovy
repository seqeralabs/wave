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
@Controller("/metrics")
class MetricController {
    @Inject
    MetricService metricsService

    @Get(uri="/pull/{metric}/count", produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map> getPullMetrics(@PathVariable String metric, @Nullable @QueryValue String startdate, @Nullable @QueryValue String enddate) {
        try {
            def result
            if(startdate && enddate) {
                result = metricsService.getPullMetrics(Metric.valueOf(metric), parseDate(startdate), parseDate(enddate))
            }else{
                result = metricsService.getPullMetrics(Metric.valueOf(metric),null, null)
            }
            if( result && result.size() > 0)
                return HttpResponse.ok(result)
            else
                return HttpResponse.notFound()
        }catch (IllegalArgumentException | DateTimeParseException e) {
            return HttpResponse.badRequest((Map)[message: e.message])
        }
    }

    @Get(uri="/build/{metric}/count", produces = MediaType.APPLICATION_JSON)
    HttpResponse<Map> getBuildMetrics(@PathVariable String metric, @Nullable @QueryValue String startdate, @Nullable @QueryValue String enddate) {
        try {
            def result
            if(startdate && enddate) {
                result = metricsService.getBuildMetrics(Metric.valueOf(metric), parseDate(startdate), parseDate(enddate))
            }else{
                result = metricsService.getBuildMetrics(Metric.valueOf(metric),null, null)
            }
            if( result && result.size() > 0)
                return HttpResponse.ok(result)
            else
                return HttpResponse.notFound()
        }catch (IllegalArgumentException | DateTimeParseException e) {
            return HttpResponse.badRequest((Map)[message: e.message])
        }
    }

    @Get(uri="/pull/count", produces = MediaType.APPLICATION_JSON)
    HttpResponse<LinkedHashMap> getPullCount(@Nullable @QueryValue String startdate, @Nullable @QueryValue String enddate) {
        try {
            if(startdate && enddate) {
                return HttpResponse.ok([count: metricsService.getPullCount(parseDate(startdate), parseDate(enddate))])
            }else{
                return HttpResponse.ok([count: metricsService.getPullCount(null, null)])
            }
        }catch (IllegalArgumentException | DateTimeParseException e) {
            return HttpResponse.badRequest([message: e.message])
        }
    }

    @Get(uri="/build/count", produces = MediaType.APPLICATION_JSON)
    HttpResponse<LinkedHashMap> getBuildCount(@Nullable @QueryValue Boolean success, @Nullable @QueryValue String startdate, @Nullable @QueryValue String enddate) {
        try {
            if(startdate && enddate) {
                return HttpResponse.ok([count: metricsService.getBuildCount(success, parseDate(startdate), parseDate(enddate))])
            }else{
                return HttpResponse.ok([count: metricsService.getBuildCount(success, null, null)])
            }
        }catch (IllegalArgumentException | DateTimeParseException e) {
            return HttpResponse.badRequest([message: e.message])
        }
    }

    private Instant parseDate(String date) {
        LocalDate localDate = LocalDate.parse(date);
        return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }
}

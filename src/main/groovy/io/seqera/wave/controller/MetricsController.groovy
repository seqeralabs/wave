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
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.AuthorizationException
import io.micronaut.security.rules.SecurityRule
import io.seqera.wave.service.metric.MetricsConstants
import io.seqera.wave.service.metric.MetricsService
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
@ExecuteOn(TaskExecutors.BLOCKING)
class MetricsController {

    @Inject
    private MetricsService metricsService

    @Deprecated
    @Get(uri = "/v1alpha2/metrics/builds", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getBuildsMetrics(@Nullable @QueryValue String date, @Nullable @QueryValue String org) {
        if(!date && !org)
            return HttpResponse.ok(metricsService.getAllOrgCount(MetricsConstants.PREFIX_BUILDS))
        return HttpResponse.ok(metricsService.getOrgCount(MetricsConstants.PREFIX_BUILDS, date, org))
    }

    @Deprecated
    @Get(uri = "/v1alpha2/metrics/pulls", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getPullsMetrics(@Nullable @QueryValue String date, @Nullable @QueryValue String org) {
        if(!date && !org)
            return HttpResponse.ok(metricsService.getAllOrgCount(MetricsConstants.PREFIX_PULLS))
        return HttpResponse.ok(metricsService.getOrgCount(MetricsConstants.PREFIX_PULLS, date, org))
    }

    @Deprecated
    @Get(uri = "/v1alpha2/metrics/fusion/pulls", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getFusionPullsMetrics(@Nullable @QueryValue String date, @Nullable @QueryValue String org) {
        if(!date && !org)
            return HttpResponse.ok(metricsService.getAllOrgCount(MetricsConstants.PREFIX_FUSION))
        return HttpResponse.ok(metricsService.getOrgCount(MetricsConstants.PREFIX_FUSION, date, org))

    }

    @Deprecated
    @Get(uri = "/v1alpha2/metrics/mirrors", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getMirrorsMetrics(@Nullable @QueryValue String date, @Nullable @QueryValue String org) {
        if(!date && !org)
            return HttpResponse.ok(metricsService.getAllOrgCount(MetricsConstants.PREFIX_MIRRORS))
        return HttpResponse.ok(metricsService.getOrgCount(MetricsConstants.PREFIX_MIRRORS, date, org))
    }

    @Deprecated
    @Get(uri = "/v1alpha2/metrics/scans", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getScansMetrics(@Nullable @QueryValue String date, @Nullable @QueryValue String org) {
        if(!date && !org)
            return HttpResponse.ok(metricsService.getAllOrgCount(MetricsConstants.PREFIX_SCANS))
        return HttpResponse.ok(metricsService.getOrgCount(MetricsConstants.PREFIX_SCANS, date, org))
    }

    @Get(uri = "/v1alpha3/metrics/builds", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getBuildsMetrics(@Nullable @QueryValue String date, @Nullable @QueryValue String org, @Nullable @QueryValue String arch) {
        if(!date && !org)
            return HttpResponse.ok(metricsService.getAllOrgCount(MetricsConstants.PREFIX_BUILDS, arch))
        return HttpResponse.ok(metricsService.getOrgCount(MetricsConstants.PREFIX_BUILDS, date, org, arch))
    }

    @Get(uri = "/v1alpha3/metrics/pulls", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getPullsMetrics(@Nullable @QueryValue String date, @Nullable @QueryValue String org, @Nullable @QueryValue String arch) {
        if(!date && !org)
            return HttpResponse.ok(metricsService.getAllOrgCount(MetricsConstants.PREFIX_PULLS, arch))
        return HttpResponse.ok(metricsService.getOrgCount(MetricsConstants.PREFIX_PULLS, date, org, arch))
    }

    @Get(uri = "/v1alpha3/metrics/fusion/pulls", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getFusionPullsMetrics(@Nullable @QueryValue String date, @Nullable @QueryValue String org, @Nullable @QueryValue String arch) {
        if(!date && !org)
            return HttpResponse.ok(metricsService.getAllOrgCount(MetricsConstants.PREFIX_FUSION, arch))
        return HttpResponse.ok(metricsService.getOrgCount(MetricsConstants.PREFIX_FUSION, date, org, arch))

    }

    @Get(uri = "/v1alpha3/metrics/mirrors", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getMirrorsMetrics(@Nullable @QueryValue String date, @Nullable @QueryValue String org, @Nullable @QueryValue String arch) {
        if(!date && !org)
            return HttpResponse.ok(metricsService.getAllOrgCount(MetricsConstants.PREFIX_MIRRORS, arch))
        return HttpResponse.ok(metricsService.getOrgCount(MetricsConstants.PREFIX_MIRRORS, date, org, arch))
    }

    @Get(uri = "/v1alpha3/metrics/scans", produces = MediaType.APPLICATION_JSON)
    HttpResponse<?> getScansMetrics(@Nullable @QueryValue String date, @Nullable @QueryValue String org, @Nullable @QueryValue String arch) {
        if(!date && !org)
            return HttpResponse.ok(metricsService.getAllOrgCount(MetricsConstants.PREFIX_SCANS, arch))
        return HttpResponse.ok(metricsService.getOrgCount(MetricsConstants.PREFIX_SCANS, date, org, arch))
    }


    @Error(exception = AuthorizationException.class)
    HttpResponse<?> handleAuthorizationException() {
        return HttpResponse.unauthorized()
                .header(WWW_AUTHENTICATE, "Basic realm=Wave Authentication")
    }

}

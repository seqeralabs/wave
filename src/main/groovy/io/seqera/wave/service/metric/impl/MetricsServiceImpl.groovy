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

package io.seqera.wave.service.metric.impl

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.service.metric.MetricConstants
import io.seqera.wave.service.metric.MetricsCounterStore
import io.seqera.wave.service.metric.MetricsService
import io.seqera.wave.service.metric.model.GetOrgCountResponse
import io.seqera.wave.tower.PlatformId
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements service to store and retrieve wave metrics from the counter store
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class MetricsServiceImpl implements MetricsService {

    @Inject
    private MetricsCounterStore metricsCounterStore

    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    @Override
    Long getBuildsMetrics(String date, String org) {
        return metricsCounterStore.get(getKey(MetricConstants.PREFIX_BUILDS, date, org)) ?: 0
    }

    @Override
    Long getPullsMetrics(String date, String org) {
        return metricsCounterStore.get(getKey(MetricConstants.PREFIX_PULLS, date, org)) ?: 0
    }

    @Override
    Long getFusionPullsMetrics(String date, String org) {
        return metricsCounterStore.get(getKey(MetricConstants.PREFIX_FUSION, date, org)) ?: 0
    }

    @Override
    GetOrgCountResponse getOrgCount(String metrics){
        GetOrgCountResponse response = new GetOrgCountResponse(metrics, 0, [:])
        def orgCounts = metricsCounterStore.getAllMatchingEntries("$metrics/$MetricConstants.PREFIX_ORG/*")
        for(def entry : orgCounts) {
            if(!entry.key.contains("/$MetricConstants.PREFIX_DAY/")) {
                response.count += entry.value
                response.orgs.put(entry.key.split("/$MetricConstants.PREFIX_ORG/").last(), entry.value)
            }
        }
        return response
    }

    @Override
    void incrementFusionPullsCounter(PlatformId platformId){
        incrementCounter(MetricConstants.PREFIX_FUSION, platformId?.user?.email)
    }

    @Override
    void incrementBuildsCounter(PlatformId platformId){
        incrementCounter(MetricConstants.PREFIX_BUILDS, platformId?.user?.email)
    }

    @Override
    void incrementPullsCounter(PlatformId platformId) {
        incrementCounter(MetricConstants.PREFIX_PULLS, platformId?.user?.email)
    }

    protected void incrementCounter(String prefix, String email) {
        def org = getOrg(email)
        def key = getKey(prefix, LocalDate.now().format(dateFormatter), null)
        metricsCounterStore.inc(key)
        log.trace("increment metrics count of: $key")
        if ( org ) {
            key = getKey(prefix, null, org)
            metricsCounterStore.inc(key)
            log.trace("increment metrics count of: $key")
            key = getKey(prefix, LocalDate.now().format(dateFormatter), org)
            metricsCounterStore.inc(key)
            log.trace("increment metrics count of: $key")
        }
    }

    protected static String getOrg(String email) {
        def matcher = email =~ /@(.+)$/
        if (matcher.find()) {
            return matcher.group(1)
        } else {
            return null
        }
    }

    protected static String getKey(String prefix, String day, String org){
        if( day && org )
            return "$prefix/$MetricConstants.PREFIX_ORG/$org/$MetricConstants.PREFIX_DAY/$day"

        if( org )
            return "$prefix/$MetricConstants.PREFIX_ORG/$org"

        if( day )
            return "$prefix/$MetricConstants.PREFIX_DAY/$day"

        return null
    }

}

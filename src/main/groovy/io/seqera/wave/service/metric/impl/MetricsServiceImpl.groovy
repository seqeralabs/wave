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
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements service to retrieve wave metrics
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
        return metricsCounterStore.get(getBuildsKey(date, org)) ?: 0
    }

    @Override
    Long getPullsMetrics(String date, String org) {
        return metricsCounterStore.get(getPullsKey(date, org)) ?: 0
    }

    @Override
    Long getFusionPullsMetrics(String date, String org) {
        return metricsCounterStore.get(getFusionPullsKey(date, org)) ?: 0
    }

    @Override
    void incrementFusionPullsCounter(String email){
        def org = getOrg(email)
        def key = getFusionPullsKey(LocalDate.now().format(dateFormatter), null)
            metricsCounterStore.inc(key)
        log.trace("increment Fusion Pulls Count: $key")
        if( org ){
            key = getFusionPullsKey(null, org)
            metricsCounterStore.inc(key)
            key = getFusionPullsKey(LocalDate.now().format(dateFormatter), org)
            metricsCounterStore.inc(key)
        }
    }

    @Override
    void incrementBuildsCounter(String email){
        def org = getOrg(email)
        def key = getBuildsKey(LocalDate.now().format(dateFormatter), null)
        metricsCounterStore.inc(key)
        log.trace("increment Builds Count: $key")
        if( org ) {
            key = getBuildsKey(null, org)
            metricsCounterStore.inc(key)
            key = getBuildsKey(LocalDate.now().format(dateFormatter), org)
            metricsCounterStore.inc(key)
        }
    }

    @Override
    void incrementPullsCounter(String email) {
        def org = getOrg(email)
        def key = getPullsKey(LocalDate.now().format(dateFormatter), null)
        metricsCounterStore.inc(key)
        log.trace("increment Pulls Count: $key")
        if ( org ) {
            key = getPullsKey(null, org)
            metricsCounterStore.inc(key)
            key = getPullsKey(LocalDate.now().format(dateFormatter), org)
            metricsCounterStore.inc(key)
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

    protected static String getFusionPullsKey(String day, String org){

        if( day && org )
            return "$MetricConstants.PREFIX_FUSION_ORG/$org/d/$day"

        if( org )
            return "$MetricConstants.PREFIX_FUSION_ORG/$org"

        if( day )
            return "$MetricConstants.PREFIX_FUSION_DAY/$day"

        return null
    }

    protected static String getBuildsKey(String day, String org){

        if( day && org )
            return "$MetricConstants.PREFIX_BUILDS_ORG/$org/d/$day"

        if( org )
            return "$MetricConstants.PREFIX_BUILDS_ORG/$org"

        if( day )
            return "$MetricConstants.PREFIX_BUILDS_DAY/$day"

        return null
    }

    protected static String getPullsKey(String day, String org){

        if( day && org )
            return "$MetricConstants.PREFIX_PULLS_ORG/$org/d/$day"

        if( org )
            return "$MetricConstants.PREFIX_PULLS_ORG/$org"

        if( day )
            return "$MetricConstants.PREFIX_PULLS_DAY/$day"

        return null
    }
}

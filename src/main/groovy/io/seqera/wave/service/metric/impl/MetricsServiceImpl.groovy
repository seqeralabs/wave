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
import java.util.regex.Matcher
import java.util.regex.Pattern

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

    static final private DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    static final private Pattern ORF_DATE_KEY_PATTERN = Pattern.compile('(builds|pulls|fusion)/o/([^/]+)/d/\\d{4}-\\d{2}-\\d{2}')

    @Inject
    private MetricsCounterStore metricsCounterStore

    @Override
    GetOrgCountResponse getAllOrgCount(String metric){
        final response = new GetOrgCountResponse(metric, 0, [:])
        final orgCounts = metricsCounterStore.getAllMatchingEntries("$metric/$MetricConstants.PREFIX_ORG/*")
        for(def entry : orgCounts) {
            // orgCounts also contains the records with org and date, so here it filter out the records with date
            if(!entry.key.contains("/$MetricConstants.PREFIX_DAY/")) {
                response.count += entry.value
                //split is used to extract the org name from the key like "metrics/o/seqera.io" => seqera.io
                response.orgs.put(entry.key.split("/$MetricConstants.PREFIX_ORG/").last(), entry.value)
            }
        }
        return response
    }

    @Override
    GetOrgCountResponse getOrgCount(String metric, String date, String org) {
        final response = new GetOrgCountResponse(metric, 0, [:])

        // count is stored per date and per org, so it can be extracted from get method
        response.count = metricsCounterStore.get(getKey(metric, date, org)) ?: 0L

        //when org and date is provided, return the org count for given date
        if (org) {
            response.orgs.put(org, response.count)
        }else{
            // when only date is provide, scan the store and return the  count for all orgs on given date
            final orgCounts = metricsCounterStore.getAllMatchingEntries("$metric/$MetricConstants.PREFIX_ORG/*/$MetricConstants.PREFIX_DAY/$date")
            for(def entry : orgCounts) {
                response.orgs.put(extractOrgFromKey(entry.key), entry.value)
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
        def key = getKey(prefix, LocalDate.now().format(DATE_FORMATTER), null)
        metricsCounterStore.inc(key)
        log.trace("increment metrics count of: $key")
        if ( org ) {
            key = getKey(prefix, null, org)
            metricsCounterStore.inc(key)
            log.trace("increment metrics count of: $key")
            key = getKey(prefix, LocalDate.now().format(DATE_FORMATTER), org)
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

    protected static String extractOrgFromKey(String key) {
        Matcher matcher = ORF_DATE_KEY_PATTERN.matcher(key)
        return matcher.matches() ? matcher.group(2) : "unknown"
    }
}

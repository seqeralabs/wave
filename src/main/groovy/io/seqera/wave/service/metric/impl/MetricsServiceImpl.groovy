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
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.metric.MetricsCounterStore
import io.seqera.wave.service.metric.MetricsService
import io.seqera.wave.service.metric.model.GetOrgArchCountResponse
import io.seqera.wave.service.metric.model.GetOrgCountResponse
import io.seqera.wave.tower.PlatformId
import jakarta.inject.Inject
import jakarta.inject.Singleton

import static io.seqera.wave.service.metric.MetricsConstants.*
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

    static final private Pattern ORG_DATE_KEY_PATTERN = Pattern.compile('(builds|pulls|fusion|mirrors|scans)/o/([^/]+).*')

    static final private Pattern ARCH_KEY_PATTERN = Pattern.compile('(builds|pulls|fusion|mirrors|scans).*/a/([^/]+).*')

    static final private Pattern ORG_ARCH_KEY_PATTERN = Pattern.compile('(builds|pulls|fusion|mirrors|scans)/o/([^/]+)/a/([^/]+).*')

    @Inject
    private MetricsCounterStore metricsCounterStore

    @Override
    GetOrgCountResponse getAllOrgCount(String metric){
        final response = new GetOrgCountResponse(metric, 0, [:])
        final orgCounts = metricsCounterStore.getAllMatchingEntries("$metric/$PREFIX_ORG/*")
        for(def entry : orgCounts) {
            // orgCounts also contains the records with arch and date, so here it filter out the records with date and arch
            if(!entry.key.contains("/$PREFIX_DAY/") && !entry.key.contains("/$PREFIX_ARCH/")) {
                response.count += entry.value
                //split is used to extract the org name from the key like "metrics/o/seqera.io" => seqera.io
                response.orgs.put(entry.key.split("/$PREFIX_ORG/").last(), entry.value)
            }
        }
        return response
    }

    @Override
    GetOrgCountResponse getOrgCount(String metric, String date, String org) {
        final response = new GetOrgCountResponse(metric, 0, [:])

        // count is stored per date and per org, so it can be extracted from get method
        response.count = metricsCounterStore.get(getKey(metric, date, org, null)) ?: 0L

        //when org and date is provided, return the org count for given date
        if (org) {
            response.orgs.put(org, response.count)
        }else{
            // when only date is provide, scan the store and return the  count for all orgs on given date
            final orgCounts = metricsCounterStore.getAllMatchingEntries("$metric/$PREFIX_ORG/*/$PREFIX_DAY/$date")
            for(def entry : orgCounts) {
                if (!entry.key.contains("/$PREFIX_ARCH/")) {
                    response.orgs.put(extractOrgFromKey(entry.key), entry.value)
                }
            }
        }

        return response

    }

    @Override
    GetOrgArchCountResponse getOrgCount(String metric, String date, String org, String arch) {
        if ( arch ) {
            arch = resolveArch(arch)
            final response = new GetOrgArchCountResponse(metric, arch, 0, [:])
            // count is stored per date and per org, so it can be extracted from get method
            response.count = metricsCounterStore.get(getKey(metric, date, org, arch)) ?: 0L
            //when org and date is provided, return the org count for given date
            if ( org ) {
                response.orgs.put(org, response.count)
            } else if ( date ){
                // when date and arch are provide, scan the store and return the  count for all orgs with given arch and date
                final orgCounts = metricsCounterStore.getAllMatchingEntries("$metric/$PREFIX_ORG/*/$PREFIX_ARCH/$arch/$PREFIX_DAY/$date")
                for (def entry : orgCounts) {
                    response.orgs.put(extractOrgFromKey(entry.key), entry.value)
                }
            } else {
                // when only arch is provide, scan the store and return the  count for all orgs with given arch
                final orgCounts = metricsCounterStore.getAllMatchingEntries("$metric/$PREFIX_ORG/*/$PREFIX_ARCH/$arch*")
                for (def entry : orgCounts) {
                    if (!entry.key.contains("/$PREFIX_DAY/")) {
                        response.orgs.put(extractOrgFromKey(entry.key), entry.value)
                    }
                }
            }
            return response
        } else {
            def response = getOrgCount(metric, date, org)
            return new GetOrgArchCountResponse(response.metric, null, response.count, response.orgs)
        }
    }

    @Override
    void incrementFusionPullsCounter(PlatformId platformId, String arch){
        incrementCounter(PREFIX_FUSION, platformId?.user?.email, arch)
    }

    @Override
    void incrementBuildsCounter(PlatformId platformId, String arch){
        incrementCounter(PREFIX_BUILDS, platformId?.user?.email, arch)
    }

    @Override
    void incrementPullsCounter(PlatformId platformId, String arch) {
        incrementCounter(PREFIX_PULLS, platformId?.user?.email, arch)
    }

    @Override
    void incrementMirrorsCounter(PlatformId platformId, String arch){
        incrementCounter(PREFIX_MIRRORS, platformId?.user?.email, arch)
    }

    @Override
    void incrementScansCounter(PlatformId platformId, String arch) {
        incrementCounter(PREFIX_SCANS, platformId?.user?.email, arch)
    }

    protected void incrementCounter(String prefix, String email, String arch) {

        final org = email ? getOrg(email) : "anonymous"
        final day = LocalDate.now().format(DATE_FORMATTER)

        //increment the count for the current day
        def key = getKey(prefix, day, null, null)
        metricsCounterStore.inc(key)
        log.trace("increment metrics count of: $key")


        if ( arch ) {
            //increment the count for the current day and arch
            key = getKey(prefix, day, null, arch)
            metricsCounterStore.inc(key)
            log.trace("increment metrics count of: $key")

            //increment the count for the current arch
            key = getKey(prefix, null, null, arch)
            metricsCounterStore.inc(key)
            log.trace("increment metrics count of: $key")
        }

        if ( org ) {
            //increment the count for the org
            key = getKey(prefix, null, org, null)
            metricsCounterStore.inc(key)
            log.trace("increment metrics count of: $key")

            //increment the count for the org and day
            key = getKey(prefix, day, org, null)
            metricsCounterStore.inc(key)
            log.trace("increment metrics count of: $key")

            if ( arch ) {
                //increment the count for the org and arch
                key = getKey(prefix, null, org, arch)
                metricsCounterStore.inc(key)
                log.trace("increment metrics count of: $key")

                //increment the count for the org and arch and current date
                key = getKey(prefix, day, org, arch)
                metricsCounterStore.inc(key)
                log.trace("increment metrics count of: $key")
            }
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

    protected static String getKey(String prefix, String day, String org, String arch){
        if ( !prefix )
            throw new IllegalArgumentException("prefix is required to construct a key")

        def key = new StringBuilder(prefix)
        if( org ) {
            key.append("/$PREFIX_ORG/$org")
        }

        if (arch) {
            if (ContainerPlatform.AMD64.contains(arch))
                key.append("/$PREFIX_ARCH/$AMD64")
            else if (ContainerPlatform.ARM64.contains(arch))
                key.append("/$PREFIX_ARCH/$ARM64")
        }

        if( day ) {
            key.append("/$PREFIX_DAY/$day")
        }

        return key.toString() != prefix ? key.toString() : null
    }

    protected static String extractOrgFromKey(String key) {
        Matcher matcher = ORG_DATE_KEY_PATTERN.matcher(key)
        return matcher.matches() ? matcher.group(2) : "unknown"
    }

    protected static String extractArchFromKey(String key) {
        Matcher matcher = ARCH_KEY_PATTERN.matcher(key)
        return matcher.matches() ? matcher.group(2) : "unknown"
    }

    protected static String extractOrgFromArchKey(String key) {
        Matcher matcher = ORG_ARCH_KEY_PATTERN.matcher(key)
        return matcher.matches() ? matcher.group(2) : "unknown"
    }

    protected static String resolveArch(String arch){
        if (ContainerPlatform.AMD64.contains(arch)) {
            return AMD64
        }
        if (ContainerPlatform.ARM64.contains(arch) || 'arm' == arch) {
            return ARM64
        }
        return null
    }
}

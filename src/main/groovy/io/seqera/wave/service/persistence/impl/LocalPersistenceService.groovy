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

package io.seqera.wave.service.persistence.impl

import groovy.transform.CompileStatic
import io.seqera.wave.core.ContainerDigestPair
import io.seqera.wave.service.metric.Metric
import io.seqera.wave.service.metric.MetricFilter
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.WaveScanRecord
import jakarta.inject.Singleton

import static io.seqera.wave.service.metric.MetricConstants.ANONYMOUS
/**
 * Basic persistence for dev purpose
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class LocalPersistenceService implements PersistenceService {

    private Map<String,WaveBuildRecord> buildStore = new HashMap<>()

    private Map<String,WaveContainerRecord> requestStore = new HashMap<>()
    private Map<String,WaveScanRecord> scanStore = new HashMap<>()

    @Override
    void saveBuild(WaveBuildRecord record) {
        buildStore[record.buildId] = record
    }

    @Override
    WaveBuildRecord loadBuild(String buildId) {
        return buildStore.get(buildId)
    }

    @Override
    WaveBuildRecord loadBuild(String buildId, String digest) {
        // TODO
        return null
    }

    @Override
    void saveContainerRequest(String token, WaveContainerRecord data) {
        requestStore.put(token, data)
    }

    @Override
    void updateContainerRequest(String token, ContainerDigestPair digest) {
        final data = requestStore.get(token)
        if( data ) {
            requestStore.put(token, new WaveContainerRecord(data, digest.source, digest.target))
        }
    }

    @Override
    WaveContainerRecord loadContainerRequest(String token) {
        requestStore.get(token)
    }

    @Override
    void createScanRecord(WaveScanRecord scanRecord) {
        scanStore.put(scanRecord.id, scanRecord)
    }

    @Override
    void updateScanRecord(WaveScanRecord scanRecord) {
        scanStore.put(scanRecord.id, scanRecord)
    }

    @Override
    WaveScanRecord loadScanRecord(String scanId) {
        scanStore.get(scanId)
    }

    @Override
    Map<String, Long> getBuildsCountByMetric(Metric metric, MetricFilter filter) {
        def builds = getFilteredBuilds(filter)
        Map<String, Long> result = null
        if(metric == Metric.ip)
            result = builds.groupBy { it.requestIp }
                    .collectEntries { [it.key, it.value.size()] }
        else if(metric == Metric.user)
            result = builds.groupBy { it.userEmail ?: ANONYMOUS }
                    .collectEntries { [it.key, it.value.size()] }

        result = result.sort{ a, b -> b.value - a.value  }
        return result.take(filter.limit)
    }

    @Override
    Long getBuildsCount(MetricFilter filter) {
        return getFilteredBuilds(filter).size()
    }

    Collection<WaveBuildRecord> getFilteredBuilds(MetricFilter filter) {
        if(!filter)
            return buildStore.values()
        def builds = buildStore.values()
        if(filter.startDate && filter.endDate)
            builds = builds.findAll { it.startTime >= filter.startDate && it.startTime <= filter.endDate }
        if(filter.success != null)
            builds = builds.findAll { it.succeeded() == filter.success }
        return builds
    }

    @Override
    Map<String, Long> getPullsCountByMetric(Metric metric, MetricFilter filter) {
        def pulls = getFilteredPulls(filter)

        Map<String, Long> result = null
        if(metric == Metric.ip)
            result = pulls.groupBy { it.ipAddress}
                    .collectEntries { [it.key, it.value.size()] }
        else if(metric == Metric.user)
            result = pulls.groupBy { it.user?.email ?: ANONYMOUS}
                    .collectEntries { [it.key, it.value.size()] }

        result = result.sort{ a, b -> b.value - a.value  }
        return result.take(filter.limit)
    }

    @Override
    Long getPullsCount(MetricFilter filter) {
        return getFilteredPulls(filter).size()
    }

    Collection<WaveContainerRecord> getFilteredPulls(MetricFilter filter) {
        if (!filter)
            return requestStore.values()
        def builds = requestStore.values()
        if(filter.startDate && filter.endDate)
            builds = builds.findAll { it.timestamp >= filter.startDate && it.timestamp <= filter.endDate }
        if(filter.fusion !=null){
            if(filter.fusion)
                builds = builds.findAll{it.containerConfig.fusionVersion() != null}
            else
                builds = builds.findAll{it.containerConfig.fusionVersion() == null}
        }

        return builds
    }
}

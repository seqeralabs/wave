/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

import java.time.Instant

import groovy.transform.CompileStatic
import io.seqera.wave.core.ContainerDigestPair
import io.seqera.wave.service.metric.Metric
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.WaveScanRecord
import jakarta.inject.Singleton
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
    Map<String, Long> getBuildCountByMetrics(Metric metric, Boolean success, Instant startDate, Instant endDate) {
        def builds = getFilteredBuilds(success, startDate, endDate)
        if(metric == Metric.ip)
            return builds.groupBy { it.requestIp }
                    .collectEntries { [it.key, it.value.size()] }

        if(metric == Metric.user)
            return builds.groupBy { it.userName}
                    .collectEntries { [it.key, it.value.size()] }

        if(metric == Metric.image)
            return builds.groupBy { it.targetImage }
                    .collectEntries { [it.key, it.value.size()] }
    }

    @Override
    Long getBuildCount(Boolean success, Instant startDate, Instant endDate) {
        def builds = getFilteredBuilds(success, startDate, endDate)
        return builds.size()
    }

    List<WaveBuildRecord> getFilteredBuilds(Boolean success, Instant startDate, Instant endDate) {
        def builds = buildStore.values()
        if(startDate && endDate)
            builds = builds.findAll { it.startTime >= startDate && it.startTime <= endDate }
        if(success != null)
            builds = builds.findAll { it.succeeded() == success }
        return builds as List<WaveBuildRecord>
    }

    @Override
    Map<String, Long> getPullCountByMetrics(Metric metric, Instant startDate, Instant endDate) {
        def pulls = getFilteredPulls(startDate, endDate)
        if(metric == Metric.ip)
            return pulls.groupBy { it.ipAddress}
                    .collectEntries { [it.key, it.value.size()] }
        if(metric == Metric.user)
            return pulls.groupBy { it.user.userName}
                    .collectEntries { [it.key, it.value.size()] }
        if(metric == Metric.image)
            return pulls.groupBy { it.sourceImage}
                    .collectEntries { [it.key, it.value.size()] }
    }

    @Override
    Long getPullCount(Instant startDate, Instant endDate) {
        def pulls = getFilteredPulls(startDate, endDate)
        return pulls.size()
    }

    @Override
    Long getDistinctMetrics(Metric metric, Instant startDate, Instant endDate) {
        def pulls = getFilteredPulls(startDate, endDate)
        if (metric == Metric.ip)
            return pulls.collect({ it.ipAddress })
                    .unique().size()
        if (metric == Metric.user)
            return pulls.collect({ it.user.userName })
                    .unique().size()
        if (metric == Metric.image)
            return pulls.collect({ it.sourceImage })
                    .unique().size()
    }

    List<WaveContainerRecord> getFilteredPulls(Instant startDate, Instant endDate) {
        def builds = requestStore.values()
        if(startDate && endDate)
            builds = builds.findAll { it.timestamp >= startDate && it.timestamp <= endDate }
        return builds as List<WaveContainerRecord>
    }
}

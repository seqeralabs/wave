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
import io.seqera.wave.service.mirror.MirrorResult
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
    private Map<String,MirrorResult> mirrorStore = new HashMap<>()

    @Override
    void saveBuild(WaveBuildRecord record) {
        buildStore[record.buildId] = record
    }

    @Override
    WaveBuildRecord loadBuild(String buildId) {
        return buildStore.get(buildId)
    }

    @Override
    WaveBuildRecord latestBuild(String containerId) {
        buildStore
                .values()
                .findAll( it-> it.buildId.startsWith(containerId) )
                .sort( it-> it.startTime )
                .reverse() [0]
    }

    @Override
    List<WaveBuildRecord> allBuilds(String containerId) {
        final pattern = ~/^(bd-)?${containerId}_[0-9]+/
        buildStore
                .values()
                .findAll( it-> pattern.matcher(it.buildId).matches() )
                .sort { it.startTime }
                .reverse()
    }

    @Override
    WaveBuildRecord loadBuildSucceed(String targetImage, String digest) {
        buildStore.values().find( (build) ->  build.targetImage==targetImage && build.digest==digest && build.succeeded() )
    }

    @Override
    void saveContainerRequest(WaveContainerRecord data) {
        requestStore.put(data.id, data)
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
    boolean existsScanRecord(String scanId) {
        scanStore.containsKey(scanId)
    }

    @Override
    void saveScanRecord(WaveScanRecord scanRecord) {
        scanStore.put(scanRecord.id, scanRecord)
    }

    @Override
    WaveScanRecord loadScanRecord(String scanId) {
        scanStore.get(scanId)
    }

    @Override
    MirrorResult loadMirrorResult(String mirrorId) {
        mirrorStore.get(mirrorId)
    }

    @Override
    MirrorResult loadMirrorSucceed(String targetImage, String digest) {
        mirrorStore.values().find( (MirrorResult mirror) ->  mirror.targetImage==targetImage && mirror.digest==digest && mirror.succeeded() )
    }

    @Override
    void saveMirrorResult(MirrorResult mirror) {
        mirrorStore.put(mirror.mirrorId, mirror)
    }

}

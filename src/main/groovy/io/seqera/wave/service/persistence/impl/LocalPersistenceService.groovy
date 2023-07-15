package io.seqera.wave.service.persistence.impl

import groovy.transform.CompileStatic
import io.seqera.wave.core.ContainerDigestPair
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

}

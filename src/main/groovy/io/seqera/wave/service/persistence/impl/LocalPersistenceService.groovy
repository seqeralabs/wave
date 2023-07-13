package io.seqera.wave.service.persistence.impl

import java.util.stream.Collectors

import groovy.transform.CompileStatic
import io.seqera.wave.core.ContainerDigestPair
import io.seqera.wave.model.ScanResult
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveContainerScanRecord
import jakarta.inject.Singleton
import io.seqera.wave.model.ScanVulnerability
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
    private Map<String,WaveContainerScanRecord> scanStore = new HashMap<>()
    private Map<String, ScanVulnerability> scanVulStore = new HashMap<>()
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
    void saveContainerScanResult(String buildId, WaveContainerScanRecord waveContainerScanRecord, List<ScanVulnerability> scanVulnerabilities) {
        scanStore.put(buildId,waveContainerScanRecord)
        scanVulnerabilities.forEach {scanVulStore.put(it.vulnerabilityId,it)}
    }

    @Override
    WaveContainerScanRecord loadContainerScanResult(String buildId) {
        scanStore.get(buildId)
    }

    @Override
    ScanResult loadContainerScanVulResult(String buildId) {
        WaveContainerScanRecord waveContainerScanRecord = loadContainerScanResult(buildId)
        List<ScanVulnerability> scanVulnerabilities = waveContainerScanRecord.scanVulnerabilitiesIds.parallelStream()
                                                                    .map {scanVulStore.get(it)}
                                                                    .collect(Collectors.toList())
        return ScanResult.load(waveContainerScanRecord.buildId,
                waveContainerScanRecord.startTime,
                waveContainerScanRecord.duration,
                waveContainerScanRecord.isSuccess,
                scanVulnerabilities)
    }
}

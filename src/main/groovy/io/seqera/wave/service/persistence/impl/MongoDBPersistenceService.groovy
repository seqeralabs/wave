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

package io.seqera.wave.service.persistence.impl

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.MongoDBConfig
import io.seqera.wave.core.ContainerDigestPair
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.persistence.repository.WaveBuildRepository
import io.seqera.wave.service.persistence.repository.WaveContainerRepository
import io.seqera.wave.service.persistence.repository.WaveScanRepository
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements a persistence service based based on SurrealDB
 *
 * @author : Munish Chouhan <munish.chouhan@seqera.io>
 */
@Requires(env='mangodb')
@Primary
@Slf4j
@Singleton
@CompileStatic
class MongoDBPersistenceService implements PersistenceService {

    @Inject
    private WaveBuildRepository waveBuildRepository

    @Inject
    private WaveContainerRepository waveContainerRepository

    @Inject
    private WaveScanRepository waveScanRepository

    @Inject
    private MongoDBConfig mongoDBConfig

    @Override
    void saveBuild(WaveBuildRecord build) {
        waveBuildRepository.save(build)
    }

    @Override
    WaveBuildRecord loadBuild(String buildId) {
        return waveBuildRepository.findById(buildId).get()
    }

    @Override
    WaveBuildRecord loadBuild(String targetImage, String digest) {
        return waveBuildRepository.findByTargetImageAndDigest(targetImage, digest).get()
    }

    @Override
    void saveContainerRequest(String token, WaveContainerRecord data) {
        waveContainerRepository.save(data)
    }

    @Override
    void updateContainerRequest(String token, ContainerDigestPair digest) {
        waveContainerRepository.updateDigest(token, digest.source, digest.target)
    }

    @Override
    WaveContainerRecord loadContainerRequest(String token) {
        return waveContainerRepository.findById(token).get()
    }

    @Override
    void createScanRecord(WaveScanRecord scanRecord) {
        waveScanRepository.save(scanRecord)
    }

    @Override
    void updateScanRecord(WaveScanRecord scanRecord) {
        waveScanRepository.update(scanRecord)
    }

    @Override
    WaveScanRecord loadScanRecord(String scanId) {
        return waveScanRepository.findById(scanId).get()
    }
}

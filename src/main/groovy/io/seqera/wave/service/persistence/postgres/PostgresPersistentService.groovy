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

package io.seqera.wave.service.persistence.postgres

import java.time.Instant

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.event.ApplicationStartupEvent
import io.micronaut.runtime.event.annotation.EventListener
import io.seqera.wave.core.ContainerDigestPair
import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.persistence.postgres.data.BuildRepository
import io.seqera.wave.service.persistence.postgres.data.BuildRow
import io.seqera.wave.service.persistence.postgres.data.MirrorRepository
import io.seqera.wave.service.persistence.postgres.data.MirrorRow
import io.seqera.wave.service.persistence.postgres.data.RequestRepository
import io.seqera.wave.service.persistence.postgres.data.RequestRow
import io.seqera.wave.service.persistence.postgres.data.ScanRepository
import io.seqera.wave.service.persistence.postgres.data.ScanRow
import io.seqera.wave.util.JacksonHelper
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(env='postgres')
@Primary
@CompileStatic
class PostgresPersistentService implements PersistenceService {

    @Inject
    private DbInitService dbInitService

    @Inject
    private BuildRepository buildRepository

    @Inject
    private RequestRepository requestRepository

    @Inject
    private MirrorRepository mirrorRepository

    @Inject
    private ScanRepository scanRepository

    @EventListener
    void onApplicationStartup(ApplicationStartupEvent event) {
        dbInitService.create()
    }

    // ===== --- build records ---- =====

    @Override
    void saveBuildAsync(WaveBuildRecord build) {
        final json = JacksonHelper.toJson(build)
        final entity = new BuildRow(id:build.buildId, data:json, createdAt: Instant.now())
        buildRepository.save(entity)
    }

    @Override
    WaveBuildRecord loadBuild(String buildId) {
        final entity = buildRepository.findById(buildId).orElse(null)
        if( !entity )
            return null
        return JacksonHelper.fromJson(entity.data, WaveBuildRecord)
    }

    @Override
    WaveBuildRecord loadBuildSucceed(String targetImage, String digest) {
        final entity = buildRepository.findByTargetAndDigest(targetImage, digest)
        if( !entity )
            return null
        return JacksonHelper.fromJson(entity.data, WaveBuildRecord)
    }

    @Override
    WaveBuildRecord latestBuild(String containerId) {
        final entity = buildRepository.findLatestByBuildId(containerId)
        if( !entity )
            return null
        return JacksonHelper.fromJson(entity.data, WaveBuildRecord)
    }

    @Override
    List<WaveBuildRecord> allBuilds(String containerId) {
        final result = buildRepository.findAllByBuildId(containerId)
        return result
                ? result.collect((it)-> JacksonHelper.fromJson(it.data, WaveBuildRecord))
                : null
    }

    // ===== --- container records ---- =====

    @Override
    void saveContainerRequestAsync(WaveContainerRecord data) {
        final json = JacksonHelper.toJson(data)
        final entity = new RequestRow(id: data.id, data:json, createdAt: Instant.now())
        requestRepository.save(entity)
    }

    @Override
    void updateContainerRequestAsync(String token, ContainerDigestPair digest) {
        requestRepository.updateContainerDigests(token, digest.source, digest.target)
    }

    @Override
    WaveContainerRecord loadContainerRequest(String token) {
        final row = requestRepository.findById(token).orElse(null)
        if( !row || !row.data )
            return null
        return JacksonHelper.fromJson(row.data, WaveContainerRecord)
    }

    // ===== --- scan records ---- =====

    @Override
    void saveScanRecordAsync(WaveScanRecord scanRecord) {
        final json = JacksonHelper.toJson(scanRecord)
        final entity = new ScanRow(id: scanRecord.id, data:json, createdAt: Instant.now())
        scanRepository.save(entity)
    }

    @Override
    WaveScanRecord loadScanRecord(String scanId) {
        final row = scanRepository.findById(scanId).orElse(null)
        if( !row || !row.data )
            return null
        return JacksonHelper.fromJson(row.data, WaveScanRecord)
    }

    @Override
    boolean existsScanRecord(String scanId) {
        return scanRepository.existsById(scanId)
    }

    @Override
    List<WaveScanRecord> allScans(String scanId) {
        final result = scanRepository.findAllByScanId(scanId)
        return result
                ? result.collect((it)-> JacksonHelper.fromJson(it.data, WaveScanRecord))
                : null
    }

    // ===== --- mirror records ---- =====

    @Override
    MirrorResult loadMirrorResult(String mirrorId) {
        final entity = mirrorRepository.findById(mirrorId).orElse(null)
        if( !entity )
            return null
        return JacksonHelper.fromJson(entity.data, MirrorResult)
    }

    @Override
    MirrorResult loadMirrorSucceed(String targetImage, String digest) {
        final entity = mirrorRepository.findByTargetAndDigest(targetImage, digest, MirrorResult.Status.COMPLETED)
        if( !entity )
            return null
        return JacksonHelper.fromJson(entity.data, MirrorResult)
    }

    @Override
    void saveMirrorResultAsync(MirrorResult mirror) {
        final json = JacksonHelper.toJson(mirror)
        final entity = new MirrorRow(id: mirror.mirrorId, data:json, createdAt: Instant.now())
        mirrorRepository.save(entity)
    }
}

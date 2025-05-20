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
import io.micronaut.core.annotation.Nullable
import io.micronaut.runtime.event.ApplicationStartupEvent
import io.micronaut.runtime.event.annotation.EventListener
import io.seqera.wave.core.ContainerDigestPair
import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.persistence.impl.SurrealPersistenceService
import io.seqera.wave.service.persistence.postgres.data.BuildRepository
import io.seqera.wave.service.persistence.postgres.data.BuildRow
import io.seqera.wave.service.persistence.postgres.data.MirrorRepository
import io.seqera.wave.service.persistence.postgres.data.MirrorRow
import io.seqera.wave.service.persistence.postgres.data.RequestRepository
import io.seqera.wave.service.persistence.postgres.data.RequestRow
import io.seqera.wave.service.persistence.postgres.data.ScanRepository
import io.seqera.wave.service.persistence.postgres.data.ScanRow
import jakarta.inject.Inject
/**
 * Implements the {@link PersistenceService} using a PostgreSQL database
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(env='postgres')
@Primary
@CompileStatic
class PostgresPersistentService implements PersistenceService {

    @Inject
    private PostgresSchemaService dbInitService

    @Inject
    private BuildRepository buildRepository

    @Inject
    private RequestRepository requestRepository

    @Inject
    private MirrorRepository mirrorRepository

    @Inject
    private ScanRepository scanRepository

    @Inject
    @Nullable
    private SurrealPersistenceService surrealPersistenceService

    @EventListener
    void onApplicationStartup(ApplicationStartupEvent event) {
        dbInitService.create()
    }

    // ===== --- build records ---- =====

    @Override
    void saveBuildAsync(WaveBuildRecord data) {
        final json = Mapper.toJson(data)
        final row = new BuildRow(id:data.buildId, data:json, createdAt: Instant.now())
        buildRepository.save(row)
    }

    @Override
    WaveBuildRecord loadBuild(String buildId) {
        final row = buildRepository.findById(buildId).orElse(null)
        if( !row ) {
            return surrealPersistenceService?.loadBuild(buildId)
        }
        return Mapper.fromJson( WaveBuildRecord, row.data, [buildId: buildId] )
    }

    @Override
    WaveBuildRecord loadBuildSucceed(String targetImage, String digest) {
        final row = buildRepository.findByTargetAndDigest(targetImage, digest)
        if( !row ){
            return surrealPersistenceService?.loadBuildSucceed(targetImage, digest)
        }
        return Mapper.fromJson( WaveBuildRecord, row.data, [buildId: row.id] )
    }

    @Override
    WaveBuildRecord latestBuild(String containerId) {
        final row = buildRepository.findLatestByBuildId(containerId)
        if( !row ){
            return surrealPersistenceService?.latestBuild(containerId)
        }
        return Mapper.fromJson( WaveBuildRecord, row.data, [buildId: row.id] )
    }

    @Override
    List<WaveBuildRecord> allBuilds(String containerId) {
        final result = buildRepository.findAllByBuildId(containerId)
        return result
                ? result.collect((it)-> Mapper.fromJson(WaveBuildRecord, it.data, [buildId: it.id]))
                : surrealPersistenceService?.allBuilds(containerId)
    }

    // ===== --- container records ---- =====

    void saveContainerRequestAsync(String id, WaveContainerRecord data) {
        final json = Mapper.toJson(data)
        final entity = new RequestRow(id: id, data:json, createdAt: Instant.now())
        requestRepository.save(entity)
    }

    @Override
    void saveContainerRequestAsync(WaveContainerRecord data) {
        final json = Mapper.toJson(data)
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
            return surrealPersistenceService?.loadContainerRequest(token)
        return Mapper.fromJson(WaveContainerRecord, row.data, [id: token])
    }

    // ===== --- scan records ---- =====

    @Override
    void saveScanRecordAsync(WaveScanRecord data) {
        final json = Mapper.toJson(data)
        final entity = new ScanRow(id: data.id, data:json, createdAt: Instant.now())
        scanRepository.save(entity)
    }

    @Override
    WaveScanRecord loadScanRecord(String scanId) {
        final row = scanRepository.findById(scanId).orElse(null)
        if( !row || !row.data )
            return surrealPersistenceService?.loadScanRecord(scanId)
        return Mapper.fromJson(WaveScanRecord, row.data, [id: scanId])
    }

    @Override
    boolean existsScanRecord(String scanId) {
        return scanRepository.existsById(scanId) ?: surrealPersistenceService?.existsScanRecord(scanId)
    }

    @Override
    List<WaveScanRecord> allScans(String scanId) {
        final result = scanRepository.findAllByScanId(scanId)
        return result
                ? result.collect((it)-> Mapper.fromJson(WaveScanRecord, it.data, [id: it.id]))
                : surrealPersistenceService?.allScans(scanId)
    }

    // ===== --- mirror records ---- =====

    @Override
    MirrorResult loadMirrorResult(String mirrorId) {
        final row = mirrorRepository.findById(mirrorId).orElse(null)
        if( !row )
            return surrealPersistenceService?.loadMirrorResult(mirrorId)
        return Mapper.fromJson(MirrorResult, row.data, [mirrorId: mirrorId])
    }

    @Override
    MirrorResult loadMirrorSucceed(String targetImage, String digest) {
        final row = mirrorRepository.findByTargetAndDigest(targetImage, digest, MirrorResult.Status.COMPLETED)
        if( !row )
            return surrealPersistenceService?.loadMirrorSucceed(targetImage, digest)
        return Mapper.fromJson(MirrorResult, row.data, [mirrorId: row.id])
    }

    @Override
    void saveMirrorResultAsync(MirrorResult data) {
        final json = Mapper.toJson(data)
        final entity = new MirrorRow(id: data.mirrorId, data:json, createdAt: Instant.now())
        mirrorRepository.save(entity)
    }
}

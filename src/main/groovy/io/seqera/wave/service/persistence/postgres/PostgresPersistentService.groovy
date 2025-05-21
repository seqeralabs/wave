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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.runtime.event.ApplicationStartupEvent
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.scheduling.TaskExecutors
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
import jakarta.inject.Named

/**
 * Implements the {@link PersistenceService} using a PostgreSQL database
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
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

    @Inject
    @Named(TaskExecutors.BLOCKING)
    private ExecutorService ioExecutor

    @EventListener
    void onApplicationStartup(ApplicationStartupEvent event) {
        dbInitService.create()
    }

    // ===== --- build records ---- =====
    private Runnable safeRun(Runnable action, GString msg) {
       new Runnable() {
           @Override
           void run() {
               try {
                   action.run()
               }
               catch (InstantiationException e) {
                   Thread.interrupted()
               }
               catch (Throwable e) {
                   log.error(msg.toString(), e)
               }
           }
       }
    }

    @Override
    CompletableFuture<Void> saveBuildAsync(WaveBuildRecord data) {
        log.trace "Saving build record=$data"
        final json = Mapper.toJson(data)
        final row = new BuildRow(id:data.buildId, data:json, createdAt: Instant.now())
        CompletableFuture.runAsync(safeRun(()->
                buildRepository.save(row),
                "Unable to save build record=$data"))
    }

    @Override
    WaveBuildRecord loadBuild(String buildId) {
        log.trace "Loading build record id=${buildId}"
        final row = buildRepository.findById(buildId).orElse(null)
        if( !row ) {
            return surrealPersistenceService?.loadBuild(buildId)
        }
        return Mapper.fromJson( WaveBuildRecord, row.data, [buildId: buildId] )
    }

    @Override
    WaveBuildRecord loadBuildSucceed(String targetImage, String digest) {
        log.trace "Loading build record with image=${targetImage}; digest=${digest}"
        final row = buildRepository.findByTargetAndDigest(targetImage, digest)
        if( !row ){
            return surrealPersistenceService?.loadBuildSucceed(targetImage, digest)
        }
        return Mapper.fromJson( WaveBuildRecord, row.data, [buildId: row.id] )
    }

    @Override
    WaveBuildRecord latestBuild(String containerId) {
        log.trace "Loading latest build with containerId=${containerId}"
        final row = buildRepository.findLatestByBuildId(containerId)
        if( !row ){
            return surrealPersistenceService?.latestBuild(containerId)
        }
        return Mapper.fromJson( WaveBuildRecord, row.data, [buildId: row.id] )
    }

    @Override
    List<WaveBuildRecord> allBuilds(String containerId) {
        log.trace "Loading all builds for containerId=${containerId}"
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
    CompletableFuture<Void> saveContainerRequestAsync(WaveContainerRecord data) {
        log.trace "Saving container request data=${data}"
        final json = Mapper.toJson(data)
        final entity = new RequestRow(id: data.id, data:json, createdAt: Instant.now())
        CompletableFuture.runAsync(safeRun(()->
                requestRepository.save(entity),
                "Unable to save container request data=$data"))
    }

    @Override
    CompletableFuture<Void> updateContainerRequestAsync(String token, ContainerDigestPair digest) {
        log.trace "Updating container request token=${token}; digest=${digest}"
        CompletableFuture.runAsync(safeRun(()->
                requestRepository.updateContainerDigests(token, digest.source, digest.target),
                "Unable to update container request token=${token}; digest=${digest}"))
    }

    @Override
    WaveContainerRecord loadContainerRequest(String token) {
        log.trace "Loading container request token=${token}"
        final row = requestRepository.findById(token).orElse(null)
        if( !row || !row.data )
            return surrealPersistenceService?.loadContainerRequest(token)
        return Mapper.fromJson(WaveContainerRecord, row.data, [id: token])
    }

    // ===== --- scan records ---- =====

    @Override
    CompletableFuture<Void> saveScanRecordAsync(WaveScanRecord data) {
        log.trace "Saving scan record data=${data}"
        final json = Mapper.toJson(data)
        final entity = new ScanRow(id: data.id, data:json, createdAt: Instant.now())
        CompletableFuture.runAsync(safeRun(()->
                scanRepository.save(entity),
                "Unable to save scan record data=${data}"))
    }

    @Override
    WaveScanRecord loadScanRecord(String scanId) {
        log.trace "Loading scan record id=${scanId}"
        final row = scanRepository.findById(scanId).orElse(null)
        if( !row || !row.data )
            return surrealPersistenceService?.loadScanRecord(scanId)
        return Mapper.fromJson(WaveScanRecord, row.data, [id: scanId])
    }

    @Override
    boolean existsScanRecord(String scanId) {
        log.trace "Exist scan record id=${scanId}"
        return scanRepository.existsById(scanId) ?: surrealPersistenceService?.existsScanRecord(scanId)
    }

    @Override
    List<WaveScanRecord> allScans(String scanId) {
        log.trace "Loading all scans with record=${scanId}"
        final result = scanRepository.findAllByScanId(scanId)
        return result
                ? result.collect((it)-> Mapper.fromJson(WaveScanRecord, it.data, [id: it.id]))
                : surrealPersistenceService?.allScans(scanId)
    }

    // ===== --- mirror records ---- =====

    @Override
    MirrorResult loadMirrorResult(String mirrorId) {
        log.trace "Loading mirror result with id=${mirrorId}"
        final row = mirrorRepository.findById(mirrorId).orElse(null)
        if( !row )
            return surrealPersistenceService?.loadMirrorResult(mirrorId)
        return Mapper.fromJson(MirrorResult, row.data, [mirrorId: mirrorId])
    }

    @Override
    MirrorResult loadMirrorSucceed(String targetImage, String digest) {
        log.trace "Loading mirror succeed with image=${targetImage}; digest=${digest}"
        final row = mirrorRepository.findByTargetAndDigest(targetImage, digest, MirrorResult.Status.COMPLETED)
        if( !row )
            return surrealPersistenceService?.loadMirrorSucceed(targetImage, digest)
        return Mapper.fromJson(MirrorResult, row.data, [mirrorId: row.id])
    }

    @Override
    CompletableFuture<Void> saveMirrorResultAsync(MirrorResult data) {
        log.trace "Saving mirror result data=${data}"
        final json = Mapper.toJson(data)
        final entity = new MirrorRow(id: data.mirrorId, data:json, createdAt: Instant.now())
        CompletableFuture.runAsync(safeRun(()->
                mirrorRepository.save(entity),
                "Unable to save mirror result data=${data}"))
    }
}

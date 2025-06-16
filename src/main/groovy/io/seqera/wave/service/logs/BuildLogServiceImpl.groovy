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

package io.seqera.wave.service.logs

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.configuration.BuildEnabled
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.persistence.PersistenceService
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.apache.commons.io.input.BoundedInputStream
import static io.seqera.wave.service.aws.ObjectStorageOperationsFactory.BUILD_LOCKS
import static io.seqera.wave.service.aws.ObjectStorageOperationsFactory.BUILD_LOGS

/**
 * Implements Service  to manage logs from an Object store
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
@Requires(bean = BuildEnabled)
class BuildLogServiceImpl implements BuildLogService {

    private static final String CONDA_LOCK_START = ">> CONDA_LOCK_START"

    private static final String CONDA_LOCK_END = "<< CONDA_LOCK_END"

    @Inject
    @Named(BUILD_LOGS)
    private ObjectStorageOperations<?, ?, ?> logsStoreOps

    @Inject
    @Named(BUILD_LOCKS)
    private ObjectStorageOperations<?, ?, ?> locksStoreOps

    @Inject
    private PersistenceService persistenceService

    @Inject
    private BuildConfig buildConfig

    @Inject
    @Named(TaskExecutors.BLOCKING)
    private ExecutorService ioExecutor

    protected String logKey(String buildId) {
        if( !buildId )
            return null
        final prefix = buildConfig?.logsPrefix
        return prefix
                ? "${prefix}/${buildId}.log"
                : buildId + '.log'
    }

    @EventListener
    void onBuildEvent(BuildEvent event) {
        if(event.result.logs) {
            CompletableFuture.supplyAsync(() -> storeLog(event.result.buildId, event.result.logs), ioExecutor)
        }
    }

    @Override
    void storeLog(String buildId, String content) {
        try {
            final String logs = removeCondaLockFile(content)
            log.debug "Storing logs for buildId: $buildId"
            final uploadRequest = UploadRequest.fromBytes(logs.bytes, logKey(buildId))
            logsStoreOps.upload(uploadRequest)
            // check if needed to store the conda lock
            final condaLock = content.contains(CONDA_LOCK_START)
            if ( condaLock )
                storeCondaLock(buildId, content)
        }
        catch (Exception e) {
            log.warn "Unable to store logs for buildId: $buildId  - reason: ${e.message}", e
        }
    }

    @Override
    StreamedFile fetchLogStream(String buildId) {
        fetchLogStream0(buildId) ?: fetchLogStream0(BuildRequest.legacyBuildId(buildId))
    }

    private StreamedFile fetchLogStream0(String buildId) {
        if( !buildId ) return null
        final Optional<ObjectStorageEntry<?>> result = logsStoreOps.retrieve(logKey(buildId))
        return result.isPresent() ? result.get().toStreamedFile() : null
    }

    @Override
    BuildLog fetchLogString(String buildId) {
        final result = fetchLogStream(buildId)
        if( !result )
            return null
        final logs = new BoundedInputStream(result.getInputStream(), buildConfig.maxLength).getText()
        return new BuildLog(logs, logs.length()>=buildConfig.maxLength)
    }

    protected static removeCondaLockFile(String logs) {
        if(logs.indexOf(CONDA_LOCK_START) < 0 ) {
            return logs
        }
        return logs.replaceAll(/(?s)\n?#\d+ \d+\.\d+ $CONDA_LOCK_START.*?$CONDA_LOCK_END\n?/, '\n')
    }

    protected void storeCondaLock(String buildId, String logs) {
        if( !logs ) return
        try {
            String condaLock = extractCondaLockFile(logs)
              /* When a container image is cached, dockerfile does not get executed.
                 In that case condalock file will contain "cat environment.lock" because its not been executed.
                 So wave will check the previous builds of that container image
                 and render the condalock file from latest successful build
                 and replace with the current build's condalock file.
               */
            if( condaLock && condaLock.contains('cat environment.lock') ){
                condaLock = fetchValidCondaLock(buildId)
            }

            if ( condaLock ){
                log.debug "Storing conda lock for buildId: $buildId"
                final uploadRequest = UploadRequest.fromBytes(condaLock.bytes, condaLockKey(buildId))
                locksStoreOps.upload(uploadRequest)
            }
        }
        catch (Exception e) {
            log.warn "Unable to store condalock for buildId: $buildId  - reason: ${e.message}", e
        }
    }

    protected String condaLockKey(String buildId) {
        if( !buildId )
            return null
        final prefix = buildConfig?.locksPrefix
        return prefix
                ? "${prefix}/${buildId}.lock"
                : buildId + '.lock'
    }

    @Override
    String fetchCondaLockString(String buildId) {
        final result = fetchCondaLockStream(buildId)
        if( !result )
            return null
        return result.getInputStream().getText()
    }

    @Override
    StreamedFile fetchCondaLockStream(String buildId) {
        if( !buildId ) return null
        final Optional<ObjectStorageEntry<?>> result = locksStoreOps.retrieve(condaLockKey(buildId))
        if( result.isPresent() )
            return result.get().toStreamedFile()
        // implements a fallback to retrieve Conda locks from bucket root 
        if( buildConfig.locksFallback ) {
            final Optional<ObjectStorageEntry<?>> fallback = locksStoreOps.retrieve(buildId + '.lock')
            if( fallback.isPresent() )
                return fallback.get().toStreamedFile()
        }
        return null
    }

    protected static String extractCondaLockFile(String logs) {
            int start = logs.lastIndexOf(CONDA_LOCK_START)
            int end = logs.lastIndexOf(CONDA_LOCK_END)
            if( start >= end ) { // when build fails, there will be commands in the logs, so to avoid extracting wrong content
                return null
            }
            return logs.substring(start + CONDA_LOCK_START.length(), end)
                    .replaceAll(/#\d+ \d+\.\d+\s*/, '')
    }

    String fetchValidCondaLock(String buildId) {
        try {
            final result = fetchValidCondaLock0(buildId)
            if( result )
                log.debug "Container Image is already cached for buildId: $buildId - uploading build's condalock file from buildId: $result"
            else
                log.warn "Container Image is already cached for buildId: $buildId - Unable to find condalock file from previous build"
            return result
        }
        catch (Throwable t) {
            log.error "Unable to determine condalock content for buildId: ${buildId} - cause: ${t.message}", t
            return null
        }
    }

    private String fetchValidCondaLock0(String buildId) {
        def builds = persistenceService.allBuilds(buildId.split('-')[1].split('_')[0])
        for (def build : builds) {
            if ( build.succeeded() ){
                def curCondaLock = fetchCondaLockString(build.buildId)
                if( curCondaLock && !curCondaLock.contains('cat environment.lock') ){
                    return curCondaLock
                }
            }
        }
        return null
    }

}

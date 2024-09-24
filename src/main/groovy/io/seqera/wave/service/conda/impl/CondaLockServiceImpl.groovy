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

package io.seqera.wave.service.conda.impl

import io.micronaut.http.MediaType
import io.micronaut.http.server.types.files.StreamedFile
import io.seqera.wave.service.conda.CondaLockService

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveCondaLockRecord
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import static io.seqera.wave.service.conda.Conda.CONDA_LOCK_END
import static io.seqera.wave.service.conda.Conda.CONDA_LOCK_START
/**
 * Implements Service  to manage conda lock files from an Object store
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class CondaLockServiceImpl implements CondaLockService {

    @Inject
    @Named(TaskExecutors.IO)
    private volatile ExecutorService ioExecutor

    @Inject
    PersistenceService persistenceService

    @EventListener
    void onBuildEvent(BuildEvent event) {
        if ( event.request.condaFile ) {
            CompletableFuture.supplyAsync(() -> storeCondaLock(event.result.id, event.result.logs), ioExecutor)
        }
    }

    @Override
    void storeCondaLock(String buildId, String logs) {
        if( !logs ) return
        try {
            String condaLock = extractCondaLockFile(logs)
            if (condaLock){
                log.debug "Storing condalock for buildId: $buildId"
                def record = new WaveCondaLockRecord(buildId, condaLock.getBytes(StandardCharsets.UTF_8))
                persistenceService.saveCondaLock(record)
            }
        }
        catch (Exception e) {
            log.warn "Unable to store condalock for buildId: $buildId  - reason: ${e.message}", e
        }
    }

    @Override
    StreamedFile fetchCondaLock(String buildId) {
        if( !buildId )
            return null
        def condaLock = persistenceService.loadCondaLock(buildId)?.condaLockFile
        if( !condaLock )
            return null
        def inputStream = new ByteArrayInputStream(condaLock)
        return new StreamedFile(inputStream, MediaType.APPLICATION_OCTET_STREAM_TYPE)

    }

    protected static extractCondaLockFile(String logs) {
        try {
            return logs.substring(logs.lastIndexOf(CONDA_LOCK_START) + CONDA_LOCK_START.length(), logs.lastIndexOf(CONDA_LOCK_END))
                    .replaceAll(/#\d+ \d+\.\d+\s*/, '')
        } catch (Exception e) {
            log.warn "Unable to extract conda lock file from logs - reason: ${e.message}", e
            return null
        }
    }

}

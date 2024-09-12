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

package io.seqera.wave.service.conda

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.service.builder.BuildEvent
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import static org.apache.commons.lang3.StringUtils.strip
/**
 * Implements Service  to manage conda lock files from an Object store
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
@Requires(property = 'wave.build.conda-lock.bucket')
class CondaLockServiceImpl implements CondaLockService {

    @Inject
    @Named('conda-lock')
    private ObjectStorageOperations<?, ?, ?> objectStorageOperations

    @Nullable
    @Value('${wave.build.conda-lock.prefix}')
    private String prefix

    @Inject
    @Named(TaskExecutors.IO)
    private volatile ExecutorService ioExecutor

    protected String condaLockKey(String buildId) {
        if( !buildId )
            return null
        if( !prefix )
            return buildId + '/' + CONDA_LOCK_FILE_NAME
        final base = strip(prefix, '/')
        return "${base}/${buildId}/${CONDA_LOCK_FILE_NAME}"
    }

    @EventListener
    void onBuildEvent(BuildEvent event) {
        if (event.request.condaFile) {
            CompletableFuture.supplyAsync(() -> storeCondaLock(event.result.id, event.result.condaLock), ioExecutor)
        }
    }

    @Override
    void storeCondaLock(String buildId, Path condaLock) {
        try {
            log.debug "Storing condalock for buildId: $buildId"
            final uploadRequest = UploadRequest.fromBytes(Files.readAllBytes(condaLock), condaLockKey(buildId))
            objectStorageOperations.upload(uploadRequest)
        }
        catch (Exception e) {
            log.warn "Unable to store condalock for buildId: $buildId  - reason: ${e.message}", e
        }
    }

    @Override
    StreamedFile fetchCondaLockStream(String buildId) {
        if( !buildId ) return null
        final Optional<ObjectStorageEntry<?>> result = objectStorageOperations.retrieve(condaLockKey(buildId))
        return result.isPresent() ? result.get().toStreamedFile() : null
    }

}

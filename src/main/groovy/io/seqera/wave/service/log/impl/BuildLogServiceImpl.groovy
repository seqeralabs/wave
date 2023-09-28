/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

package io.seqera.wave.service.log.impl

import java.nio.charset.StandardCharsets
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.UploadResponse
import io.micronaut.runtime.event.annotation.EventListener
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.log.LogService
import io.seqera.wave.service.persistence.PersistenceService
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.apache.commons.io.IOUtils

/**
 * Implements Service  to manage logs from an Object store
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class BuildLogServiceImpl implements LogService{

    @Inject
    @Named('build-logs')
    private final ObjectStorageOperations<?, ?, ?> objectStorageOperations

    @Inject
    PersistenceService persistenceService

    @EventListener
    void onBuildEvent(BuildEvent event) {
        try {
            if( event.result.succeeded() ) {
                String key = storeLog(event.result.id, event.result.logs)
                log.info "logs has been stored for buildId: ${event.result.id} with key: ${key}"
            }
        }
        catch (Exception e) {
            log.warn "Unable to run the container scan - reason: ${e.message?:e}"
        }
    }

    @Override
    String storeLog(String buildId, String log){
        UploadRequest uploadRequest = UploadRequest.fromBytes(log.getBytes(),buildId)
        UploadResponse response = objectStorageOperations.upload(uploadRequest)
        return response.key
    }

    @Override
    String fetchLog(String buildId) {
        StreamedFile streamedFile = objectStorageOperations.retrieve(buildId).get().toStreamedFile()
        if (streamedFile !=null ) {
            IOUtils.toString(streamedFile.inputStream, StandardCharsets.UTF_8)
        }else{
            return null
        }
    }
}

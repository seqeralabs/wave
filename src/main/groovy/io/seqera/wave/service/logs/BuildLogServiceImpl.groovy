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

package io.seqera.wave.service.logs

import java.util.concurrent.CompletableFuture
import javax.annotation.Nullable

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.runtime.event.annotation.EventListener
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.persistence.PersistenceService
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.apache.commons.io.input.BoundedInputStream
/**
 * Implements Service  to manage logs from an Object store
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
@Requires(property = 'wave.build.logs.bucket')
class BuildLogServiceImpl implements BuildLogService {

    @Inject
    @Named('build-logs')
    private ObjectStorageOperations<?, ?, ?> objectStorageOperations

    @Inject
    private PersistenceService persistenceService

    @Nullable
    @Value('${wave.build.logs.prefix}')
    private String prefix

    @Value('${wave.build.logs.bucket}')
    private String bucket

    @Value('${wave.build.logs.maxLength:100000}')
    private long maxLength

    @PostConstruct
    private void init() {
        log.info "Creating Build log service bucket=$bucket; prefix=$prefix"
    }

    protected String logKey(String buildId) {
        if( !buildId )
            return null
        if( !prefix )
            return buildId + '.log'
        final base = org.apache.commons.lang3.StringUtils.strip(prefix, '/')
        return "${base}/${buildId}.log"
    }

    @EventListener
    void onBuildEvent(BuildEvent event) {
        if( event.result.succeeded() ) {
            final buildId = event.result.id
            CompletableFuture.supplyAsync(()-> storeLog(buildId, event.result.logs))
        }
    }

    @Override
    void storeLog(String buildId, String content){
        try {
            log.debug "Storing logs for buildId: $buildId"
            final uploadRequest = UploadRequest.fromBytes(content.getBytes(), logKey(buildId))
            objectStorageOperations.upload(uploadRequest)
        }
        catch (Exception e) {
            log.warn "Unable to store logs for buildId: $buildId  - reason: ${e.message}", e
        }
    }

    @Override
    StreamedFile fetchLogStream(String buildId) {
        final Optional<ObjectStorageEntry<?>> result = objectStorageOperations.retrieve(logKey(buildId))
        return result.isPresent() ? result.get().toStreamedFile() : null
    }

    @Override
    BuildLog fetchLogString(String buildId) {
        final result = fetchLogStream(buildId)
        if( !result )
            return null
        final logs = new BoundedInputStream(result.getInputStream(), maxLength).getText()
        return new BuildLog(logs, logs.length()>=maxLength)
    }
}

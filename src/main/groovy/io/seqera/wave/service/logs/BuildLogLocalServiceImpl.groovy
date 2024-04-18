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

package io.seqera.wave.service.logs

import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.http.MediaType
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.runtime.event.annotation.EventListener
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildStrategy
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.apache.commons.io.input.BoundedInputStream
/**
 * Implements Service  to manage logs from local storage
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
@Requires(missingProperty =  'wave.build.logs.bucket')
class BuildLogLocalServiceImpl  implements BuildLogService {

    @Inject
    private BuildStrategy buildStrategy

    @Value('${wave.build.logs.maxLength:100000}')
    private long maxLength

    Map<String, String> logStore = new ConcurrentHashMap()

    @EventListener
    void onBuildEvent(BuildEvent event) {
        if(event.result.logs) {
            CompletableFuture.supplyAsync(() -> storeLog(event.result.id, event.result.logs))
        }
    }

    @Override
    void storeLog(String buildId, String log) {
        logStore.put(buildId, log)
    }

    @Override
    StreamedFile fetchLogStream(String buildId) {
        //replace all regex is removing color from log otherwise it will not be displayed correctly in browser
        final logs = logStore.get(buildId) ?: buildStrategy.getLogs(buildId).replaceAll("\u001B\\[[;\\d]*m", "")
        if( !logs )
            return null
        def inputStream = new ByteArrayInputStream(logs.getBytes(StandardCharsets.UTF_8))
        return inputStream ? new StreamedFile(inputStream, MediaType.TEXT_HTML_TYPE) : null
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

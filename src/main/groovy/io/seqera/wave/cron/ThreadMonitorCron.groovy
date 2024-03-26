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

package io.seqera.wave.cron

import java.nio.file.Path
import java.time.Duration

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import org.apache.commons.compress.utils.FileNameUtils
import org.apache.commons.io.FilenameUtils
/**
 * Implement basic cron that checks the periodically the number of live thread
 * and dump the pool stack traces when it reaches a given threshold
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(property = 'endpoints.metrics.enabled', value = 'true')
@Context
@CompileStatic
class ThreadMonitorCron {

    @Value('${wave.thread-monitor.dump-threshold:500}')
    private Integer dumpThreshold

    @Value('${wave.thread-monitor.dump-file}')
    @Nullable
    private String dumpFile

    @Value('${micronaut.server.port}')
    private int port

    @Value('${wave.thread-monitor.interval:5m}')
    private Duration interval

    @Inject
    @Client("/")
    private HttpClient client

    @PostConstruct
    void init0() {
        log.info "Creating check thread monitor cron - dump-interval: $interval; dump-threshold: $dumpThreshold; dump-file: $dumpFile; port: $port"
    }

    @Scheduled(fixedRate = '${wave.thread-monitor.interval:5m}', initialDelay = '${wave.thread-monitor.interval:5m}')
    void monitor() {
        final request =  HttpRequest.GET("http://localhost:$port/metrics/jvm.threads.live")
        final json = client.toBlocking().exchange(request, String)
        final resp = new JsonSlurper().parseText(json.body()) as Map
        final m = (resp.measurements as List).first() as Map
        final v = m.value as Integer
        String msg = "Current jvm.threads.live value: $v"
        if( v >= dumpThreshold ) {
            final result = dumpThreads()
            if( dumpFile ) {
                final file = Path.of(getDumpFile(dumpFile))
                msg += " - check file $file"
                file.text = result
            }
            else {
                msg += "\n${result}"
            }
            log.warn(msg)
        }
        else {
            log.debug(msg)
        }
    }

    private String dumpThreads() {
        def buffer = new StringBuffer()
        Map<Thread, StackTraceElement[]> m = Thread.getAllStackTraces();
        for(Map.Entry<Thread,  StackTraceElement[]> e : m.entrySet()) {
            buffer.append('\n').append(e.getKey().toString()).append('\n')
            for (StackTraceElement s : e.getValue()) {
                buffer.append("  " + s).append('\n')
            }
        }

        return buffer.toString()
    }

    static protected String getDumpFile(String file) {
        if( !file )
            throw new IllegalArgumentException("Missing thread monitor dump file name")
        if( !file.startsWith('/'))
            throw new IllegalArgumentException("Dump file name should start with a '/'")
        def dir = FilenameUtils.getFullPath(file)
        if( !dir.startsWith('/') )
            dir = '/' + dir
        final name = FileNameUtils.getBaseName(file)
        final ext = FileNameUtils.getExtension(file) ?: 'txt'
        return dir + name + '-' + System.currentTimeMillis() + '.' + ext
    }

}

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

package io.seqera.wave.service.persistence.impl

import com.fasterxml.jackson.core.type.TypeReference
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.event.ApplicationStartupEvent
import io.micronaut.runtime.event.annotation.EventListener
import io.seqera.wave.core.ContainerDigestPair
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.persistence.legacy.SurrealLegacyService
import io.seqera.wave.service.scan.ScanVulnerability
import io.seqera.wave.util.JacksonHelper
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements a persistence service based based on SurrealDB
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 * @author : Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(env='surrealdb')
@Primary
@Slf4j
@Singleton
@CompileStatic
class SurrealPersistenceService implements PersistenceService {

    @Inject
    private SurrealClient surrealDb

    @Value('${surreal.default.user}')
    private String user

    @Value('${surreal.default.password}')
    private String password

    @Nullable
    @Value('${surreal.default.init-db}')
    private Boolean initDb

    @Inject
    @Nullable
    private SurrealLegacyService legacy

    @EventListener
    void onApplicationStartup(ApplicationStartupEvent event) {
        if (initDb)
            initializeDb()
    }

    void initializeDb(){
        // create wave_build table
        final ret1 = surrealDb.sqlAsMap(authorization, "define table wave_build SCHEMALESS")
        if( ret1.status != "OK")
            throw new IllegalStateException("Unable to define SurrealDB table wave_build - cause: $ret1")
        // create wave_request table
        final ret2 = surrealDb.sqlAsMap(authorization, "define table wave_request SCHEMALESS")
        if( ret2.status != "OK")
            throw new IllegalStateException("Unable to define SurrealDB table wave_request - cause: $ret2")
        // create wave_scan table
        final ret3 = surrealDb.sqlAsMap(authorization, "define table wave_scan SCHEMALESS")
        if( ret3.status != "OK")
            throw new IllegalStateException("Unable to define SurrealDB table wave_scan - cause: $ret3")
        // create wave_scan table
        final ret4 = surrealDb.sqlAsMap(authorization, "define table wave_scan_vuln SCHEMALESS")
        if( ret4.status != "OK")
            throw new IllegalStateException("Unable to define SurrealDB table wave_scan_vuln - cause: $ret3")

    }

    private String getAuthorization() {
        "Basic "+"$user:$password".bytes.encodeBase64()
    }

    @Override
    void saveBuild(WaveBuildRecord build) {
        surrealDb.insertBuildAsync(authorization, build).subscribe({ result->
            log.trace "Build record saved ${result}"
        }, {error->
            def msg = error.message
            if( error instanceof HttpClientResponseException ){
                msg += ":\n $error.response.body"
            }
            log.error "Error saving build record ${msg}\n${build}", error
        })
    }

    void saveBuildBlocking(WaveBuildRecord record) {
        surrealDb.insertBuild(getAuthorization(), record)
    }

    WaveBuildRecord loadBuild(String buildId) {
        if( !buildId )
            throw new IllegalArgumentException("Missing 'buildId' argument")
        final query = "select * from wave_build where buildId = '$buildId'"
        final json = surrealDb.sqlAsString(getAuthorization(), query)
        final type = new TypeReference<ArrayList<SurrealResult<WaveBuildRecord>>>() {}
        final data= json ? JacksonHelper.fromJson(json, type) : null
        final result = data && data[0].result ? data[0].result[0] : null
        if( !result && legacy )
            return legacy.loadBuild(buildId)
        return result
    }

    @Override
    void saveContainerRequest(String token, WaveContainerRecord data) {
        surrealDb.insertContainerRequestAsync(authorization, token, data).subscribe({ result->
            log.trace "Container request with token '$token' saved record: ${result}"
        }, {error->
            def msg = error.message
            if( error instanceof HttpClientResponseException ){
                msg += ":\n $error.response.body"
            }
            log.error("Error saving container request record ${msg}\n${data}", error)
        })
    }

    void updateContainerRequest(String token, ContainerDigestPair digest) {
        final query = """\
                                UPDATE wave_request:$token SET 
                                    sourceDigest = '$digest.source',
                                    waveDigest = '${digest.target}'
                                """.stripIndent()
        surrealDb
                .sqlAsync(getAuthorization(), query)
                .subscribe({result ->
                    log.trace "Container request with token '$token' updated record: ${result}"
                },
                {error->
                    def msg = error.message
                    if( error instanceof HttpClientResponseException ){
                        msg += ":\n $error.response.body"
                    }
                    log.error("Error update container record=$token => ${msg}\ndigest=${digest}\n", error)
                })
    }

    @Override
    WaveContainerRecord loadContainerRequest(String token) {
        if( !token )
            throw new IllegalArgumentException("Missing 'token' argument")
        final json = surrealDb.getContainerRequest(getAuthorization(), token)
        log.trace "Container request with token '$token' loaded: ${json}"
        final type = new TypeReference<ArrayList<SurrealResult<WaveContainerRecord>>>() {}
        final data= json ? JacksonHelper.fromJson(json, type) : null
        final result = data && data[0].result ? data[0].result[0] : null
        if( !result && legacy )
            return legacy.loadContainerRequest(token)
        return result
    }

    void createScanRecord(WaveScanRecord scanRecord) {
        final result = surrealDb.insertScanRecord(authorization, scanRecord)
        log.trace "Scan create result=$result"
    }

    @Override
    void updateScanRecord(WaveScanRecord scanRecord) {
        final vulnerabilities = scanRecord.vulnerabilities ?: List.<ScanVulnerability>of()

        // save all vulnerabilities
        for( ScanVulnerability it : vulnerabilities ) {
            surrealDb.insertScanVulnerability(authorization, it)
        }

        // compose the list of ids
        final ids = vulnerabilities
                .collect(it-> "wave_scan_vuln:⟨$it.id⟩")
                .join(', ')

        // create the scan record
        final statement = """\
                                UPDATE wave_scan:${scanRecord.id} 
                                SET 
                                    status = '${scanRecord.status}',
                                    duration = '${scanRecord.duration}',
                                    vulnerabilities = ${ids ? "[$ids]" : "[]" } 
                                """.stripIndent()
        final result = surrealDb.sqlAsMap(authorization, statement)
        log.trace "Scan update result=$result"
    }

    @Override
    WaveScanRecord loadScanRecord(String scanId) {
        if( !scanId )
            throw new IllegalArgumentException("Missing 'scanId' argument")
        final statement = "SELECT * FROM wave_scan:$scanId FETCH vulnerabilities"
        final json = surrealDb.sqlAsString(getAuthorization(), statement)
        final type = new TypeReference<ArrayList<SurrealResult<WaveScanRecord>>>() {}
        final data= json ? JacksonHelper.fromJson(json, type) : null
        final result = data && data[0].result ? data[0].result[0] : null
        if( !result && legacy )
            return legacy.loadScanRecord(scanId)
        return result
    }

}

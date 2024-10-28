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
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.mirror.MirrorEntry
import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.WaveScanRecord
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
            throw new IllegalStateException("Unable to define SurrealDB table wave_scan_vuln - cause: $ret4")
        // create wave_mirror table
        final ret5 = surrealDb.sqlAsMap(authorization, "define table wave_mirror SCHEMALESS")
        if( ret5.status != "OK")
            throw new IllegalStateException("Unable to define SurrealDB table wave_mirror - cause: $ret5")
    }

    protected String getAuthorization() {
        "Basic "+"$user:$password".bytes.encodeBase64()
    }

    @Override
    void saveBuild(WaveBuildRecord build) {
        // note: use surreal sql in order to by-pass issue with large payload
        // see https://github.com/seqeralabs/wave/issues/559#issuecomment-2369412170
        final query = "INSERT INTO wave_build ${JacksonHelper.toJson(build)}"
        surrealDb
                .sqlAsync(getAuthorization(), query)
                .subscribe({result ->
                    log.trace "Build request with id '$build.buildId' saved record: ${result}"
                },
                        {error->
                            def msg = error.message
                            if( error instanceof HttpClientResponseException ){
                                msg += ":\n $error.response.body"
                            }
                            log.error("Error saving Build request record ${msg}\n${build}", error)
                        })
    }

    @Override
    WaveBuildRecord loadBuild(String buildId) {
        if( !buildId )
            throw new IllegalArgumentException("Missing 'buildId' argument")
        def result = loadBuild0(buildId)
        if( result )
            return result
        // try to lookup legacy record
        final legacyId = BuildRequest.legacyBuildId(buildId)
        return legacyId ? loadBuild1(legacyId) : null
    }

    private WaveBuildRecord loadBuild0(String buildId) {
        final query = "select * from wave_build where buildId = '$buildId'"
        final json = surrealDb.sqlAsString(getAuthorization(), query)
        final type = new TypeReference<ArrayList<SurrealResult<WaveBuildRecord>>>() {}
        final data= json ? JacksonHelper.fromJson(json, type) : null
        final result = data && data[0].result ? data[0].result[0] : null
        return result
    }

    private WaveBuildRecord loadBuild1(String buildId) {
        final query = "select * from wave_build where buildId = '$buildId'"
        final json = surrealDb.sqlAsString(getAuthorization(), query)
        final type = new TypeReference<ArrayList<SurrealResult<WaveBuildRecord>>>() {}
        final data= json ? JacksonHelper.fromJson(json, type) : null
        final result = data && data[0].result ? data[0].result[0] : null
        return result
    }

    @Override
    WaveBuildRecord loadBuildSucceed(String targetImage, String digest) {
        final query = """\
            select * from wave_build 
            where   
                targetImage = '$targetImage' 
                and digest = '$digest'
                and exitStatus = 0 
                and duration is not null
            order by 
               startTime desc limit 1
            """.stripIndent()
        final json = surrealDb.sqlAsString(getAuthorization(), query)
        final type = new TypeReference<ArrayList<SurrealResult<WaveBuildRecord>>>() {}
        final data= json ? JacksonHelper.fromJson(json, type) : null
        final result = data && data[0].result ? data[0].result[0] : null
        return result
    }

    @Override
    WaveBuildRecord latestBuild(String containerId) {
        final query = """
            select * 
            from wave_build 
            where buildId ~ '${containerId}${BuildRequest.SEP}'
            order by startTime desc limit 1
            """.stripIndent()
        final json = surrealDb.sqlAsString(getAuthorization(), query)
        final type = new TypeReference<ArrayList<SurrealResult<WaveBuildRecord>>>() {}
        final data= json ? JacksonHelper.fromJson(json, type) : null
        final result = data && data[0].result ? data[0].result[0] : null
        return result
    }

    @Override
    List<WaveBuildRecord> allBuilds(String containerId) {
        final query = """
            select * 
            from wave_build 
            where string::matches(buildId, '^(bd-)?${containerId}_[0-9]+')
            order by startTime desc
            """.stripIndent()
        final json = surrealDb.sqlAsString(getAuthorization(), query)
        final type = new TypeReference<ArrayList<SurrealResult<WaveBuildRecord>>>() {}
        final data= json ? JacksonHelper.fromJson(json, type) : null
        final result = data && data[0].result ? data[0].result : null
        return result ? Arrays.asList(result) : null
    }

    @Override
    void saveContainerRequest(WaveContainerRecord data) {
        // note: use surreal sql in order to by-pass issue with large payload
        // see https://github.com/seqeralabs/wave/issues/559#issuecomment-2369412170
        final query = "INSERT INTO wave_request ${JacksonHelper.toJson(data)}"
        surrealDb
                .sqlAsync(getAuthorization(), query)
                .subscribe({result ->
                    log.trace "Container request with token '$data.id' saved record: ${result}"
                },
                        {error->
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
        final data= json ? JacksonHelper.fromJson(patchSurrealId(json,"wave_request"), type) : null
        final result = data && data[0].result ? data[0].result[0] : null
        return result
    }

    static protected String patchSurrealId(String json, String table) {
        return json.replaceFirst(/"id":\s*"${table}:(\w*)"/) { List<String> it-> /"id":"${it[1]}"/ }
    }

    @Override
    void saveScanRecord(WaveScanRecord scanRecord) {
        final vulnerabilities = scanRecord.vulnerabilities ?: List.<ScanVulnerability>of()

        // save all vulnerabilities
        for( ScanVulnerability it : vulnerabilities ) {
            surrealDb.insertScanVulnerability(authorization, it)
        }

        // compose the list of ids
        final ids = vulnerabilities
                .collect(it-> "wave_scan_vuln:⟨$it.id⟩".toString())


        // scan object
        final copy = scanRecord.clone()
        copy.vulnerabilities = List.of()
        final json = JacksonHelper.toJson(copy)

        // create the scan record
        final statement = "INSERT INTO wave_scan ${patchScanVulnerabilities(json, ids)}".toString()
        final result = surrealDb.sqlAsMap(authorization, statement)
        log.trace "Scan update result=$result"
    }

    protected String patchScanVulnerabilities(String json, List<String> ids) {
        final value = "\"vulnerabilities\":${ids.collect(it-> "\"$it\"").toString()}"
        json.replaceFirst(/"vulnerabilities":\s*\[]/, value)
    }

    @Override
    boolean existsScanRecord(String scanId) {
        final statement = "SELECT count() FROM wave_scan where id == 'wave_scan:⟨$scanId⟩'"
        final json = surrealDb.sqlAsString(getAuthorization(), statement)
        final type = new TypeReference<ArrayList<SurrealResult<Map>>>() {}
        final data= json ? JacksonHelper.fromJson(json, type) : null
        final result = data && data[0].result ? data[0].result[0].count==1 : false
        return result
    }

    @Override
    WaveScanRecord loadScanRecord(String scanId) {
        if( !scanId )
            throw new IllegalArgumentException("Missing 'scanId' argument")
        final statement = "SELECT * FROM wave_scan where id == 'wave_scan:⟨$scanId⟩' FETCH vulnerabilities"
        final json = surrealDb.sqlAsString(getAuthorization(), statement)
        final type = new TypeReference<ArrayList<SurrealResult<WaveScanRecord>>>() {}
        final data= json ? JacksonHelper.fromJson(json, type) : null
        final result = data && data[0].result ? data[0].result[0] : null
        return result
    }

    @Override
    List<WaveScanRecord> allScans(String scanId) {
        final query = """
            select * 
            from wave_scan
            where string::matches(type::string(id), '^wave_scan:⟨(sc-)?${scanId}_[0-9]+')
            order by startTime desc
            FETCH vulnerabilities
            """.stripIndent()
        final json = surrealDb.sqlAsString(getAuthorization(), query)
        final type = new TypeReference<ArrayList<SurrealResult<WaveScanRecord>>>() {}
        final data= json ? JacksonHelper.fromJson(json, type) : null
        final result = data && data[0].result ? data[0].result : null
        return result ? Arrays.asList(result) : null
    }

    // ===  mirror operations

    /**
     * Load a mirror state record
     *
     * @param mirrorId The ID of the mirror record
     * @return The corresponding {@link MirrorEntry} object or null if it cannot be found
     */
    MirrorResult loadMirrorResult(String mirrorId) {
        final query = "select * from wave_mirror where mirrorId = '$mirrorId'"
        final json = surrealDb.sqlAsString(getAuthorization(), query)
        final type = new TypeReference<ArrayList<SurrealResult<MirrorResult>>>() {}
        final data= json ? JacksonHelper.fromJson(json, type) : null
        final result = data && data[0].result ? data[0].result[0] : null
        return result
    }

    /**
     * Load a mirror state record given the target image name and the image digest.
     * It returns the latest succeed mirror result.
     *
     * @param targetImage The target mirrored image name
     * @param digest The image content SHA256 digest
     * @return The corresponding {@link MirrorEntry} object or null if it cannot be found
     */
    MirrorResult loadMirrorSucceed(String targetImage, String digest) {
        final query = """
            select * from wave_mirror 
            where 
                targetImage = '$targetImage' 
                and digest = '$digest'
                and exitCode = 0
                and status = '${MirrorResult.Status.COMPLETED}'
            order by 
                creationTime desc limit 1
            """
        final json = surrealDb.sqlAsString(getAuthorization(), query)
        final type = new TypeReference<ArrayList<SurrealResult<MirrorResult>>>() {}
        final data= json ? JacksonHelper.fromJson(json, type) : null
        final result = data && data[0].result ? data[0].result[0] : null
        return result
    }

    /**
     * Persists a {@link MirrorEntry} object
     *
     * @param mirror {@link MirrorEntry} object
     */
    @Override
    void saveMirrorResult(MirrorResult mirror) {
        surrealDb.insertMirrorAsync(getAuthorization(), mirror).subscribe({ result->
            log.trace "Mirror request with id '$mirror.mirrorId' saved record: ${result}"
        }, {error->
            def msg = error.message
            if( error instanceof HttpClientResponseException ){
                msg += ":\n $error.response.body"
            }
            log.error("Error saving Mirror request record ${msg}\n${mirror}", error)
        })
    }

}

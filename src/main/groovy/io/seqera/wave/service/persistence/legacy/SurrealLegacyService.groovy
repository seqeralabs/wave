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

package io.seqera.wave.service.persistence.legacy

import com.fasterxml.jackson.core.type.TypeReference
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveContainerRecord
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.persistence.impl.SurrealResult
import io.seqera.wave.util.JacksonHelper
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements a persistence service based based on SurrealDB
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Requires(property = 'surreal.legacy.url')
@Primary
@Slf4j
@Singleton
@CompileStatic
class SurrealLegacyService  {

    @Inject
    private SurrealLegacyClient surrealDb

    @Value('${surreal.legacy.user}')
    private String user

    @Value('${surreal.legacy.password}')
    private String password


    private String getAuthorization() {
        "Basic "+"$user:$password".bytes.encodeBase64()
    }

    WaveBuildRecord loadBuild(String buildId) {
        if( !buildId )
            throw new IllegalArgumentException("Missing 'buildId' argument")
        final query = "select * from wave_build where buildId = '$buildId'"
        final json = surrealDb.sqlAsString(getAuthorization(), query)
        final type = new TypeReference<ArrayList<SurrealResult<WaveBuildRecord>>>() {}
        final data= json ? JacksonHelper.fromJson(patchDuration(json), type) : null
        final result = data && data[0].result ? data[0].result[0] : null
        return result
    }

    static protected String patchDuration(String value) {
        if( !value )
            return value
        // Yet another SurrealDB bug: it wraps number values with double quotes as a string
        value.replaceAll(/"duration":"(\d+\.\d+)"/,'"duration":$1')
    }

    WaveContainerRecord loadContainerRequest(String token) {
        if( !token )
            throw new IllegalArgumentException("Missing 'token' argument")
        final json = surrealDb.getContainerRequest(getAuthorization(), token)
        log.trace "Container request with token '$token' loaded: ${json}"
        final type = new TypeReference<ArrayList<SurrealResult<WaveContainerRecord>>>() {}
        final data= json ? JacksonHelper.fromJson(json, type) : null
        final result = data && data[0].result ? data[0].result[0] : null
        return result
    }

    WaveScanRecord loadScanRecord(String scanId) {
        if( !scanId )
            throw new IllegalArgumentException("Missing 'scanId' argument")
        final statement = "SELECT * FROM wave_scan:$scanId FETCH vulnerabilities"
        final json = surrealDb.sqlAsString(getAuthorization(), statement)
        final type = new TypeReference<ArrayList<SurrealResult<WaveScanRecord>>>() {}
        final data= json ? JacksonHelper.fromJson(patchDuration(json), type) : null
        final result = data && data[0].result ? data[0].result[0] : null
        return result
    }

}

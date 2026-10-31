/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.service.soci

import java.nio.file.Path
import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.seqera.random.LongRndKey
/**
 * Model the request to build the zTOC of one image layer
 *
 * @param requestId The unique id of the request, used to name the job
 * @param source The layer reference e.g. {@code docker.io/library/ubuntu@sha256:...}
 * @param layerDigest The layer digest
 * @param layerMediaType The layer media type
 * @param layerSize The layer size in bytes
 * @param spanSize The zTOC span size
 * @param minLayerSize The minimum size of an indexed layer
 * @param repository The repository where the zTOC artifact is pushed
 * @param workDir The job work directory, holding the docker auth file
 * @param authJson The docker config json authorising the layer pull and the zTOC push
 * @param creationTime When the request was created
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@ToString(includeNames = true, includePackage = false, excludes = ['authJson'])
@CompileStatic
class SociIndexRequest {

    static final String ID_PREFIX = 'soci-'

    String requestId
    String source
    String layerDigest
    String layerMediaType
    long layerSize
    long spanSize
    long minLayerSize
    String repository
    Path workDir
    String authJson
    Instant creationTime

    /**
     * @return The key identifying the zTOC of this layer
     */
    String getKey() {
        return SociIndexRequest.key(layerDigest, spanSize)
    }

    static String key(String layerDigest, long spanSize) {
        return "${spanSize}/${layerDigest}"
    }

    static SociIndexRequest create(String source, String layerDigest, String layerMediaType, long layerSize, long spanSize, long minLayerSize, String repository, Path workspace, String authJson) {
        final id = ID_PREFIX + LongRndKey.rndHex()
        return new SociIndexRequest(
                id,
                source,
                layerDigest,
                layerMediaType,
                layerSize,
                spanSize,
                minLayerSize,
                repository,
                workspace.resolve(id),
                authJson,
                Instant.now() )
    }
}

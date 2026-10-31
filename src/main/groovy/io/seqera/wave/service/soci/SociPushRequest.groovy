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
 * Model the request to publish the SOCI-enabled tag of a durable (freeze or mirror) container
 *
 * @param requestId The unique id of the request, used to name the job
 * @param targetImage The container image e.g. {@code quay.io/user/app:1.0}
 * @param sociImage The SOCI-enabled image to be pushed e.g. {@code quay.io/user/app:1.0-soci}
 * @param workDir The job work directory, holding the docker auth file and the push plan
 * @param authJson The docker config json authorising the push
 * @param creationTime When the request was created
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@ToString(includeNames = true, includePackage = false, excludes = ['authJson'])
@CompileStatic
class SociPushRequest {

    static final String ID_PREFIX = 'socipush-'

    String requestId
    String targetImage
    String sociImage
    Path workDir
    String authJson
    Instant creationTime

    static SociPushRequest create(String targetImage, String sociImage, Path workspace, String authJson) {
        final id = ID_PREFIX + LongRndKey.rndHex()
        return new SociPushRequest(id, targetImage, sociImage, workspace.resolve(id), authJson, Instant.now())
    }
}

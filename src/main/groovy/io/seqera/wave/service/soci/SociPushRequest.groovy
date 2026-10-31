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
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.PlatformId
/**
 * Model the request to publish the SOCI-enabled tag of a durable (freeze or mirror) container
 *
 * @param requestId The unique id of the request, used to name the push job
 * @param targetImage The container image e.g. {@code quay.io/user/app:1.0}
 * @param sociImage The SOCI-enabled image to be pushed e.g. {@code quay.io/user/app:1.0-soci}
 * @param platform The container platform
 * @param identity The identity of the requester
 * @param workDir The push job work directory, holding the docker auth file and the push plan
 * @param creationTime When the request was created
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@ToString(includeNames = true, includePackage = false)
@CompileStatic
class SociPushRequest {

    static final String ID_PREFIX = 'socipush-'

    String requestId
    String targetImage
    String sociImage
    ContainerPlatform platform
    PlatformId identity
    Path workDir
    Instant creationTime

    static SociPushRequest create(String targetImage, String sociImage, ContainerPlatform platform, PlatformId identity, Path workspace) {
        final id = ID_PREFIX + LongRndKey.rndHex()
        return new SociPushRequest(id, targetImage, sociImage, platform, identity, workspace.resolve(id), Instant.now())
    }
}

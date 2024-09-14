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

package io.seqera.wave.service.mirror

import java.nio.file.Path
import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.seqera.wave.util.LongRndKey
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includeNames = true, includePackage = false)
@Canonical
@CompileStatic
class MirrorRequest {

    final String id
    final String sourceImage
    final String targetImage
    final Path workDir
    final String configJson
    final Instant creationTime

    static MirrorRequest create(String sourceImage, String targetImage, Path workspace, String configJson, Instant ts=Instant.now()) {
        assert sourceImage, "Argument 'sourceImage' cannot be null"
        assert targetImage, "Argument 'targetImage' cannot be empty"

        final id = LongRndKey.rndHex()
        return new MirrorRequest(
                id,
                sourceImage,
                targetImage,
                workspace.resolve("mirror-${id}"),
                configJson,
                ts )
    }
}

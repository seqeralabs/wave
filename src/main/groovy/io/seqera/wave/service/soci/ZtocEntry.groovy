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

import groovy.transform.Canonical
import groovy.transform.CompileStatic
/**
 * Model the zTOC (SOCI table of contents) of one image layer
 *
 * @param layerDigest The digest of the image layer
 * @param layerMediaType The media type of the image layer
 * @param ztocDigest The digest of the zTOC blob
 * @param ztocSize The size of the zTOC blob in bytes
 * @param spanSize The span size used to build the zTOC
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class ZtocEntry {
    String layerDigest
    String layerMediaType
    String ztocDigest
    long ztocSize
    long spanSize
}

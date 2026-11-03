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
 * The documents making an image SOCI-enabled (SOCI index manifest v2)
 *
 * @param manifestJson The image manifest annotated with the SOCI index digest
 * @param manifestDigest The digest of the annotated image manifest
 * @param manifestSize The size of the annotated image manifest
 * @param sociIndexJson The SOCI index manifest
 * @param sociIndexDigest The digest of the SOCI index manifest
 * @param sociIndexSize The size of the SOCI index manifest
 * @param ztocs The zTOCs referenced by the SOCI index manifest
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class SociDocs {
    String manifestJson
    String manifestDigest
    int manifestSize
    String sociIndexJson
    String sociIndexDigest
    int sociIndexSize
    List<ZtocEntry> ztocs

    /** The SOCI index manifest config, the empty JSON object */
    String getConfigJson() { SociManifestAssembler.EMPTY_JSON }

    String getConfigDigest() { SociManifestAssembler.EMPTY_JSON_DIGEST }
}

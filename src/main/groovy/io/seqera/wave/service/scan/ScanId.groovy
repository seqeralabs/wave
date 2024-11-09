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

package io.seqera.wave.service.scan

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.seqera.wave.util.RegHelper

/**
 * Model a scan operation id
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class ScanId {

    final String base
    final int count

    ScanId withCount(int count) {
        new ScanId(this.base, count)
    }

    String toString() {
        "sc-${base}_${count}"
    }

    static ScanId of(String containerImage, String digest=null){
        final data = new LinkedHashMap(2)
        data.image = containerImage
        if( digest )
            data.digest = digest
        final x = RegHelper.sipHash(data)
        return new ScanId(x)
    }

}

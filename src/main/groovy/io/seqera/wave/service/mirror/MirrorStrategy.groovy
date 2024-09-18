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

import groovy.transform.CompileStatic

/**
 * Implement the common strategy to handle container mirror
 * via Skopeo
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
abstract class MirrorStrategy {

    abstract void mirrorJob(String jobName, MirrorRequest request)

    protected List<String> copyCommand(MirrorRequest request) {
        final result = new ArrayList<String>(20)
        if( request.platform ) {
            result.add("--override-os")
            result.add(request.platform.os)
            result.add("--override-arch")
            result.add(request.platform.arch)
            if( request.platform.variant ) {
                result.add("--override-variant")
                result.add(request.platform.variant)
            }
        }

        result.add("copy")
        result.add("--preserve-digests")
        result.add("--multi-arch")
        result.add( request.platform ? 'system' : 'all')
        result.add("docker://${request.sourceImage}".toString())
        result.add("docker://${request.targetImage}".toString())

        return result
    }
}

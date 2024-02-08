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

package io.seqera.wave.service.builder

import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.tower.PlatformId
/**
 * Implement container freeze service to augment a container build at runtime
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface FreezeService {

    /**
     * Creates a new freeze build from the current container request. A freeze build
     * modifies the submitted container file adding the dependencies defined by the
     * {@link SubmitContainerTokenRequest#containerConfig} object.
     * 
     * @param req
     *      The container request as submitted by the user
     * @return
     *      A new instance of {@link SubmitContainerTokenRequest} having the container file modified
     *      to include the changes defined by the {@link SubmitContainerTokenRequest#containerConfig} object
     *      or the original request object if the request does provide an empty config object 
     */
    SubmitContainerTokenRequest freezeBuildRequest(final SubmitContainerTokenRequest req, PlatformId identity)

}

/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.service.builder

import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.tower.User

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
    SubmitContainerTokenRequest freezeBuildRequest(final SubmitContainerTokenRequest req, final User user)

}

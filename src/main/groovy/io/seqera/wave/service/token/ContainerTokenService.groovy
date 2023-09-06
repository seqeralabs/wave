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

package io.seqera.wave.service.token

import io.seqera.wave.service.ContainerRequestData

/**
 * service to fulfill request for an augmented container
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface ContainerTokenService {

    /**
     * Get (generate) a new container token for the specified container request data
     *
     * @param request An instance of {@link io.seqera.wave.service.ContainerRequestData}
     * @return A new token string that's used to track this request
     */
    TokenData computeToken(ContainerRequestData request)

    /**
     * Get the container image for the given container token
     *
     * @param token A container token string
     * @return the corresponding token string, or null if the token is unknown
     */
    ContainerRequestData getRequest(String token)

}

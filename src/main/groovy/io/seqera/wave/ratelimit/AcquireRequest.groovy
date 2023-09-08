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

package io.seqera.wave.ratelimit

import groovy.transform.Canonical
import groovy.transform.CompileStatic


/**
 * A simple bean to contain the userId and Ip of a request
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Canonical
@CompileStatic
class AcquireRequest {

    /**
     * Principal key to use in the search. Can be null
     */
    String userId

    /**
     * Secondary key to use if principal is not present
     */
    String ip

}

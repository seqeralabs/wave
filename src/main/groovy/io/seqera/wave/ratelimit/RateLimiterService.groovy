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

import io.seqera.wave.exception.SlowDownException


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
interface RateLimiterService {

    void acquireBuild(AcquireRequest request) throws SlowDownException

    void acquirePull(AcquireRequest request) throws SlowDownException
}

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

package io.seqera.wave.tower.auth

import groovy.transform.Canonical

/**
 * Models JWT authorization tokens
 * used to connect with Tower service
 */
@Canonical
class JwtAuth {

    /**
     * The bearer authorization token
     */
    String bearer

    /**
     * The refresh token to request an updated authorization token
     */
    String refresh
    
}

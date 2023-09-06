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

package io.seqera.wave.core

import groovy.transform.Canonical

/**
 * Hold the container digest that originated the request and the
 * digest of the resolved container provisioned by wave
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
class ContainerDigestPair {
    /**
     * Digest of the source container image
     */
    final String source

    /**
     * Digest of the augmented container image
     */
    final String target
}

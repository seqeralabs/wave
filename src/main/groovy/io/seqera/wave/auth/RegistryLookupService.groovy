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

package io.seqera.wave.auth

/**
 * Lookup service for container registry
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface RegistryLookupService {

    /**
     * Given a registry name lookup for the corresponding
     * auth endpoint
     *
     * @param registry
 *         The registry name e.g. {@code docker.io} or {@code quay.io}
     * @return The corresponding {@link RegistryAuth} object holding the realm URI and service info,
     *     or {@code null} if nothing is found
     */
    RegistryInfo lookup(String registry)

}

/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.service.pairing

import io.seqera.service.pairing.exchange.PairingResponse

/**
 * Provides public key generation for tower credentials integration.
 *
 * Once {@link PairingService#acquirePairingKey(java.lang.String, java.lang.String)} is
 * called a new {@link PairingRecord} for the requested service is generated and cached until it expires.
 *
 * Further invocation of {@link PairingService#acquirePairingKey(java.lang.String, java.lang.String)}
 * will not generate a new {@code KeyRecord} and return instead the public side of the already
 * generated one.
 *
 * Access to the currently generated {@code KeyRecord} for the corresponding service is provided
 * through {@link PairingService#getPairingRecord(java.lang.String, java.lang.String)}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface PairingService {

    public static String TOWER_SERVICE = "tower"

    /**
     * Generates an return a key pair for the provided {@code service} available
     * at {@code endpoint}
     *
     * The key-pair is generated only if it is not already available for (service,endpoint)
     * otherwise the current key is returned.
     *
     * @param service The service name
     * @param endpoint The endpoint of the service
     * @return {@link PairingResponse} with the generated encoded public key
     */
    PairingResponse acquirePairingKey(String service, String endpoint)

    /**
     * Get the {@link PairingRecord} associated with {@code service} and {@code endpoint}
     * generated with {@link #getPairingRecord(java.lang.String, java.lang.String)}
     *
     * @param service The service name
     * @param endpoint The endpoint of the service
     * @return {@link PairingRecord} if it has been generated and not expired, {@code null} otherwise
     */
    PairingRecord getPairingRecord(String service, String endpoint)

}

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

package io.seqera.wave.service.pairing


import io.seqera.wave.exchange.PairingResponse
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

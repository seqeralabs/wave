/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

package io.seqera.wave.tower.client

import groovy.transform.CompileStatic

/**
 * Models an encrypted credentials keys response
 *
 * @author Andrea Tortorella <andrea.tortorella@seqera.io>
 */
@CompileStatic
class GetCredentialsKeysResponse {

    /**
     * Secret keys associated with the credentials
     * The keys are encrypted using {@link io.seqera.tower.crypto.AsymmetricCipher}
     */
    String keys
}

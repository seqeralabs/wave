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
    String user

    /**
     * Secondary key to use if principal is not present
     */
    String ip

}

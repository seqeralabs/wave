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

package io.seqera.wave.proxy

import groovy.transform.EqualsAndHashCode
import io.seqera.wave.encoder.MoshiSerializable
/**
 * Model a response object to be forwarded to the client
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@EqualsAndHashCode
class DelegateResponse implements MoshiSerializable {
    int statusCode
    Map<String,List<String>> headers
    byte[] body
    String location
    boolean isRedirect() { location }
    boolean isCacheable() { location!=null || (body!=null && statusCode>=200 && statusCode<400) }

    @Override
    public String toString() {
        return "DelegateResponse[" +
                "statusCode=" + statusCode +
                ", location=" + (location ? "'${location}'" : "null") +
                ", body=" + (body != null ? "[byte array: ${body.length}]" : "null") +
                ", headers=" + headers +
                "]";
    }
}

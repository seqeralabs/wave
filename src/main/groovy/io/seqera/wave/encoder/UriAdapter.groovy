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

package io.seqera.wave.encoder


import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
/**
 * Mosh adapter for {@link URI} class
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class UriAdapter {

    @ToJson
    String serialize(URI uri) {
        return uri != null ? uri.toString() : null
    }

    @FromJson
    URI deserialize(String data) {
        return data != null ? URI.create(data) : null
    }

}

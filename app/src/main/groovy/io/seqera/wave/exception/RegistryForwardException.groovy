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

package io.seqera.wave.exception

import java.net.http.HttpResponse

import groovy.transform.Canonical
import groovy.transform.CompileStatic
/**
 * Exception thrown when a error response is returned by a target registry
 * while authenticating a container request
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class RegistryForwardException extends WaveException implements HttpError {

    final int statusCode
    final String response
    final Map<String,String> headers

    RegistryForwardException(String message, int status, String body, Map<String,List<String>> headers) {
        super(message)
        this.statusCode = status
        this.response = body
        this.headers = simpleMap(headers) ?: Map.<String,String>of()
    }

    RegistryForwardException(String message, HttpResponse<String> resp) {
        super(message)
        this.statusCode = resp.statusCode()
        this.response = resp.body()
        this.headers = simpleMap(resp.headers().map())
    }

    private Map<String,String> simpleMap(Map<String,List<String>> h) {
        final result = new LinkedHashMap<String,String>()
        for( Map.Entry<String,List<String>> it : h.entrySet()) {
            result.put(it.key, it.value?.first() ?: '')
        }
        return result
    }

    String getMessage() {
        def result = super.getMessage()
        result += " - status=${statusCode}; response=${response}; headers=${headers}"
        return result
    }
}

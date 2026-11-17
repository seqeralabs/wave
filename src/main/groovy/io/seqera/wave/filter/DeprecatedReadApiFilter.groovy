/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.filter

import groovy.transform.CompileStatic
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter

/**
 * Marks the read endpoints superseded by /v1alpha3, which applies the access check.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@ServerFilter(patterns = [
        '/v1alpha1/builds/**',
        '/v1alpha1/mirrors/**',
        '/v1alpha1/scans/**',
        '/v1alpha2/container/**',
        '/container-token/**' ])
@CompileStatic
class DeprecatedReadApiFilter {

    @ResponseFilter
    void markDeprecated(HttpRequest<?> request, MutableHttpResponse<?> response) {
        response.header('Deprecation', 'true')
        response.header('Link', '</v1alpha3>; rel="successor-version"')
    }

}

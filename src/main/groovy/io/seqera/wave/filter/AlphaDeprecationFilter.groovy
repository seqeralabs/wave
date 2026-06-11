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
import groovy.util.logging.Slf4j
import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter

/**
 * Stamp {@code Deprecation: true} and {@code Sunset} headers on responses from
 * pre-v1 (alpha or unversioned) API paths.
 *
 * Alpha paths remain functional for in-the-wild Nextflow/Tower clients but are
 * no longer the documented surface.  The headers make client maintainers aware
 * they should migrate to the {@code /w1/*} stable API.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@ServerFilter([
    '/v1alpha1/**',
    '/v1alpha2/**',
    '/v1alpha3/**',
    '/container-token',
    '/container-token/**',
    '/service-info',
    '/validate-creds',
    '/scans',
    '/inspect'
])
class AlphaDeprecationFilter implements Ordered {

    static final String SUNSET_VALUE = 'Sat, 31 May 2027 23:59:59 GMT'

    @Override
    int getOrder() {
        return FilterOrder.ALPHA_DEPRECATION
    }

    @ResponseFilter
    void responseFilter(HttpResponse<?> response) {
        if (response instanceof MutableHttpResponse) {
            final MutableHttpResponse<?> mutable = (MutableHttpResponse<?>) response
            mutable.header('Deprecation', 'true')
            mutable.header('Sunset', SUNSET_VALUE)
            if( log.isTraceEnabled() )
                log.trace "Stamped Deprecation + Sunset headers on response"
        }
    }
}

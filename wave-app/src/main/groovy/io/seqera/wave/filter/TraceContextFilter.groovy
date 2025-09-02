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

package io.seqera.wave.filter

import java.util.regex.Pattern

import groovy.transform.CompileStatic
import io.micronaut.context.propagation.slf4j.MdcPropagationContext
import io.micronaut.core.propagation.MutablePropagatedContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import org.slf4j.MDC
import static io.micronaut.http.annotation.ServerFilter.MATCH_ALL_PATTERN

/**
 * HTTP filter to trace and propagate request metadata in the MDC logging context
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@ServerFilter(MATCH_ALL_PATTERN)
class TraceContextFilter {

    static final Pattern REGEX = ~/^\/v2\/wt\/([a-z0-9]+).*/

    @RequestFilter
    void requestFilter(HttpRequest<?> request, MutablePropagatedContext mutablePropagatedContext) {
        try {
            final requestId = getRequestId(request.path)
            MDC.put("requestId", requestId)
            MDC.put("requestPath", request.path)
            MDC.put("requestMethod", request.methodName)
            mutablePropagatedContext.add(new MdcPropagationContext())
        } finally {
            MDC.remove("requestId")
            MDC.remove("requestPath")
            MDC.remove("requestMethod")
        }
    }

    static String getRequestId(String path) {
        final m = REGEX.matcher(path)
        return m.matches() ? m.group(1) : null
    }

}

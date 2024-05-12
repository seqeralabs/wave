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

package io.seqera.wave.trace

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import jakarta.inject.Singleton

/**
 * Implements an method interceptor that logs the elapsed time to carry out the execution
 * of method invocation.
 *
 * This interceptor is applied to classes or methods marked in the {@link TraceElapsedTime} annotation
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Slf4j
@Singleton
@InterceptorBean(TraceElapsedTime)
class TraceElapsedTimeInterceptor implements MethodInterceptor<Object,Object> {

    @Override
    Object intercept(MethodInvocationContext<Object, Object> context) {
        // get the threshold value from the TraceElapsedTime annotation
        final annot = context.getAnnotation(TraceElapsedTime)
        final threshold = annot.intValue('threshold').orElse(0)

        // apply it
        final begin = System.currentTimeMillis()
        try {
            return context.proceed()
        }
        finally {
            final delta = System.currentTimeMillis() - begin
            if( threshold==0 ) {
                log.debug "Method ${trace(context)} elapsed time ${Duration.ofMillis(delta)}"
            }
            else if( delta>=threshold ) {
                log.warn "Method ${trace(context)} elapsed time ${Duration.ofMillis(delta)}"
            }
        }
    }

    static private String trace(MethodInvocationContext<Object, Object> context) {
        context.getDeclaringType().getSimpleName() + '.' + context.getMethodName() + '(' + context.getParameterValueMap().entrySet().join(',')  + ')'
    }
}

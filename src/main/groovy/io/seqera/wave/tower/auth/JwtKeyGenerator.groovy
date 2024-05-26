/*
 * Copyright (c) 2019-2020, Seqera Labs.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.tower.auth

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.aop.chain.MethodInterceptorChain
import io.micronaut.cache.interceptor.CacheKeyGenerator
import io.micronaut.cache.interceptor.DefaultCacheKeyGenerator
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.Introspected
/**
 * A with {@link CacheKeyGenerator} that only includes the field {@link JwtAuth#endpoint},
 * {@link JwtAuth#bearer} and {@link JwtAuth#refresh} when using {@link JwtAuth} class,
 * and use default behavior for any other class.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Introspected
@CompileStatic
class JwtKeyGenerator implements CacheKeyGenerator {

    DefaultCacheKeyGenerator delegate = new DefaultCacheKeyGenerator()

    @Override
    Object generateKey(AnnotationMetadata meta, Object... params) {
        if( meta instanceof MethodInterceptorChain ) {
            final ret = delegate.generateKey(meta, patch(params))
            log.debug "JWT cache key=${ret.hashCode()} - params=$params"
            return ret
        }
        throw new IllegalArgumentException()
    }

    protected Object[] patch(Object... params) {
        final result = new Object[params.size()]
        for( int i=0; i<params.length; i++ ) {
            final it = params[i]
            if( it instanceof JwtAuth ) {
                final auth = it as JwtAuth
                final fields = new ArrayList(10)
                fields.add(auth.endpoint)
                fields.add(auth.bearer)
                fields.add(auth.refresh)
                result[i] = fields.join('.')
            }
            else {
                result[i] = params[i]
            }
        }
        return result
    }
}

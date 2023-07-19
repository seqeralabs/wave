package io.seqera.wave.service.aws

import groovy.transform.CompileStatic
import io.micronaut.aop.chain.MethodInterceptorChain
import io.micronaut.cache.interceptor.CacheKeyGenerator
import io.micronaut.cache.interceptor.DefaultCacheKeyGenerator
import io.micronaut.cache.interceptor.ParametersKey
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.Introspected
/**
 * A with {@link CacheKeyGenerator} which includes the class and method method in the cache key
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Introspected
@CompileStatic
class FullyQualifiedKeyGenerator implements CacheKeyGenerator {

    DefaultCacheKeyGenerator delegate = new DefaultCacheKeyGenerator()

    @Override
    Object generateKey(AnnotationMetadata meta, Object... params) {
        if( meta instanceof MethodInterceptorChain ) {
            final className = ((MethodInterceptorChain) meta).getExecutableMethod().getDeclaringType()
            final methodName = ((MethodInterceptorChain) meta).getExecutableMethod().getMethodName()
            final val = delegate.generateKey(meta,params)
            final result = [className, methodName, val] as Object[]
            return new ParametersKey(result)
        }
        throw new IllegalArgumentException()
    }
}

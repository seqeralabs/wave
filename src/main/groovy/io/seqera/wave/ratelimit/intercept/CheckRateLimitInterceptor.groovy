package io.seqera.wave.ratelimit.intercept

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.aop.InterceptorBean
import io.micronaut.aop.MethodInterceptor
import io.micronaut.aop.MethodInvocationContext
import io.seqera.wave.ratelimit.AcquireBuildRateLimit
import io.seqera.wave.ratelimit.impl.RateLimiterService
import jakarta.inject.Singleton

/**
 * Interceptor of methods annotated as AcquireBuildRateLimit to check if current request are allowed or rate limit
 * has been reached
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Singleton
@Slf4j
@InterceptorBean(AcquireBuildRateLimit)
@CompileStatic
class CheckRateLimitInterceptor implements MethodInterceptor<Object, Object> {

    private final RateLimiterService rateLimiter

    CheckRateLimitInterceptor(RateLimiterService rateLimiter) {
        this.rateLimiter = rateLimiter
    }

    @Override
    Object intercept(MethodInvocationContext<Object, Object> context) {
        if( context.hasAnnotation(AcquireBuildRateLimit)) {

            final def key = context.getAnnotation(AcquireBuildRateLimit).stringValue("keyInArg").get()

            def id = context.parameters.values().stream()
                    .filter( x-> x.name==key)
                    .findFirst()
                    .orElseThrow()
                    .value.toString()

            checkRateLimit(id)
        }
        context.proceed()
    }


    private void checkRateLimit(String id){
        rateLimiter.acquireBuild(id)
    }
}

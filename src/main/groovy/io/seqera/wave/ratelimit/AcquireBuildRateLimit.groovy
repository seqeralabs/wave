package io.seqera.wave.ratelimit

import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.Target

import io.micronaut.aop.Around
import static java.lang.annotation.ElementType.METHOD
import static java.lang.annotation.RetentionPolicy.RUNTIME

/**
 * Annotation to acquire resources for a build
 *
 * Given a key (i.e. userId, 'anonymous', etc) control how many requests are run in a given period.
 *
 * This key is extracted from the named argument of the method as `keyInArg`:
 *
 * @AcquireBuildRateLimit(keyInArg='userId')
 * public void runTaskForUser(Task task, int memory, String userId)
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */

@Documented
@Retention(RUNTIME)
@Target(METHOD)
@Around
@interface AcquireBuildRateLimit {

    String keyInArg()

}

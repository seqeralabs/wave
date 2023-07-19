package io.seqera.wave.service.aws

import com.amazonaws.services.simpleemail.AmazonSimpleEmailService
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder
import groovy.transform.CompileStatic
import io.micronaut.cache.annotation.Cacheable

/**
 * Implements a factory class for creating AWS client
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Singleton
@CompileStatic
class AwsClientFactory {
    // AWS SES
    @Cacheable(cacheNames = 'client-factory', keyGenerator = FullyQualifiedKeyGenerator)
    AmazonSimpleEmailService getEmailClient() {
        return AmazonSimpleEmailServiceClientBuilder
                .standard()
                .build()
    }
}

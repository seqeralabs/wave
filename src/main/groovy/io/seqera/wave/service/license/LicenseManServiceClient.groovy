package io.seqera.wave.service.license

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.annotation.Client
import io.micronaut.retry.annotation.Retryable

/**
 * Declare service for license manager
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Requires(property = 'license.server.url')
@Header(name = "User-Agent", value = "Wave service")
@Client(value = '${license.server.url}')
@Retryable(
        delay = '${license.retry.delay:1s}',
        maxDelay = '${license.retry.maxDelay:10s}',
        attempts = '${license.retry.attempts:3}',
        multiplier = '${license.retry.multiplier:1.5}')
interface LicenseManServiceClient {

    @Get('/check?token={token}&product={product}')
    HttpResponse checkToken(String token, String product)
}

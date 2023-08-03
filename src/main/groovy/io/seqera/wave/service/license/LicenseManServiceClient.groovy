package io.seqera.wave.service.license

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Get
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
@Retryable
interface LicenseManServiceClient {

    @Get('/check?token={token}&product={product}')
    HttpResponse checkToken(String token, String product)
}

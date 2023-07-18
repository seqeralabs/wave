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
@Client(id = "licenseman")
@Retryable
interface LicenseManServiceClient {

    @Get('/check?token={token}&product={product}')
    HttpResponse checkToken(String token, String product)
}

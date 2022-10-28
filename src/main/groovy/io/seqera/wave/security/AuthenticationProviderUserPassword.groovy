package io.seqera.wave.security

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink


/**
 * An authentication service based in user/password
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Slf4j
@CompileStatic
@Singleton
class AuthenticationProviderUserPassword implements AuthenticationProvider {

    @Value('${wave.security.username}')
    String username

    @Value('${wave.security.password}')
    String password

    @Override
    Publisher<AuthenticationResponse> authenticate(@Nullable HttpRequest<?> httpRequest,
                                                   AuthenticationRequest<?, ?> authenticationRequest) {
        Flux.create({ emitter ->
            if ( authenticationRequest.identity == username && authenticationRequest.secret == password ) {
                emitter.next(AuthenticationResponse.success((String) authenticationRequest.identity))
                emitter.complete()
            } else {
                emitter.error(AuthenticationResponse.exception())
            }

        }, FluxSink.OverflowStrategy.ERROR)
    }

}

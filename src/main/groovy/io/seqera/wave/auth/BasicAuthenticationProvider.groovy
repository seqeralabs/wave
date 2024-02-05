package io.seqera.wave.auth

import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.AuthenticationProvider
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.seqera.wave.configuration.AuthConfig
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink

@Singleton
@Requires(property = "wave.auth.basic.enabled", value = "true")
class BasicAuthenticationProvider implements AuthenticationProvider {
    @Inject
    AuthConfig authConfig

    @Override
    Publisher<AuthenticationResponse> authenticate(@Nullable HttpRequest<?> httpRequest,
                                                   AuthenticationRequest<?, ?> authenticationRequest) {
        Flux.create(emitter -> {
            if (authenticationRequest.identity == authConfig.userName && authenticationRequest.secret == authConfig.password) {
                emitter.next(AuthenticationResponse.success((String) authenticationRequest.identity))
                emitter.complete()
            } else {
                emitter.error(AuthenticationResponse.exception())
            }
        }, FluxSink.OverflowStrategy.ERROR)
    }
}

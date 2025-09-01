/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.auth

import groovy.util.logging.Slf4j
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpRequest
import io.micronaut.security.authentication.AuthenticationFailureReason
import io.micronaut.security.authentication.AuthenticationRequest
import io.micronaut.security.authentication.AuthenticationResponse
import io.micronaut.security.authentication.provider.HttpRequestAuthenticationProvider
import io.seqera.wave.service.account.AccountService
import io.seqera.wave.util.StringUtils
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Basic Authentication provider
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
class BasicAuthenticationProvider<B> implements HttpRequestAuthenticationProvider<B> {

    @Inject
    private AccountService accountService

    @Override
    AuthenticationResponse authenticate(@Nullable HttpRequest<B> httpRequest, @NonNull AuthenticationRequest<String, String> authRequest) {
        final user = authRequest.identity?.toString()
        final pass = authRequest.secret?.toString()
        if (accountService.isAuthorised(user, pass)) {
            log.trace "Auth request OK - user '$user'; password: '${StringUtils.redact(pass)}'"
            return AuthenticationResponse.success(authRequest.identity)
        }
        else {
            log.trace "Auth request FAILED - user '$user'; password: '${StringUtils.redact(pass)}'"
            return AuthenticationResponse.failure(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH)
        }
    }
}

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

package io.seqera.wave.tower.auth

import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.api.ContainerInspectRequest
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.util.DigestFunctions
import static io.seqera.wave.util.StringUtils.trunc
/**
 * Models JWT authorization tokens
 * used to connect with Tower service
 */
@Slf4j
@Canonical
@CompileStatic
class JwtAuth {

    final String key

    /**
     * The target endpoint
     */
    final String endpoint

    /**
     * The bearer authorization token
     */
    final String bearer

    /**
     * The refresh token to request an updated authorization token
     */
    final String refresh

    /**
     * When this token was first acquired by the system. This field should only be
     * set when a refresh token is given because it's used to determine until when
     * Wave should continue to keep the jwt token updated
     */
    final Instant createdAt

    /**
     * The instant when the objct was last updated
     */
    final Instant updatedAt

    JwtAuth withKey(String value) {
        new JwtAuth(value, endpoint, bearer, refresh, createdAt, updatedAt)
    }

    JwtAuth withBearer(String value) {
        new JwtAuth(key, endpoint, value, refresh, createdAt, updatedAt)
    }

    JwtAuth withRefresh(String value) {
        new JwtAuth(key, endpoint, bearer, value, createdAt, updatedAt)
    }

    JwtAuth withCreatedAt(Instant value) {
        new JwtAuth(key, endpoint, bearer, refresh, value, updatedAt)
    }

    JwtAuth withUpdatedAt(Instant value) {
        new JwtAuth(key, endpoint, bearer, refresh, createdAt, value)
    }

    @Override
    String toString() {
        return "JwtAuth{" +
                "key='" + key + '\'' +
                ", endpoint='" + endpoint + '\'' +
                ", bearer='" + trunc0(bearer) + '\'' +
                ", refresh='" + trunc0(refresh) + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    private String trunc0(String value) {
        return log.isTraceEnabled() ? value : trunc(value,25)
    }

    static String key(String endpoint, String token) {
        return DigestFunctions.md5("${endpoint}:${token}")
    }

    static String key(JwtAuth auth) {
        return key(auth.endpoint, auth.bearer)
    }

    static String key(SubmitContainerTokenRequest req) {
        return key(req.towerEndpoint, req.towerAccessToken)
    }

    static JwtAuth of(PlatformId platformId) {
        new JwtAuth(
                key(platformId.towerEndpoint, platformId.towerEndpoint),
                platformId.towerEndpoint,
                platformId.accessToken,
                platformId.refreshToken,
        )
    }

    static JwtAuth of(ContainerInspectRequest req) {
        new JwtAuth(
                key(req.towerEndpoint, req.towerAccessToken),
                req.towerEndpoint,
                req.towerAccessToken )
    }

    static JwtAuth of(String endpoint, String token, String refresh=null) {
        new JwtAuth(
                key(endpoint, token),
                endpoint,
                token,
                refresh)
    }

    static JwtAuth from(SubmitContainerTokenRequest req) {
        return new JwtAuth(
                key(req.towerEndpoint,req.towerAccessToken),
                req.towerEndpoint,
                req.towerAccessToken,
                req.towerRefreshToken )
    }
}

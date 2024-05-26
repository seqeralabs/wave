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
import io.seqera.wave.api.ContainerInspectRequest
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.util.DigestFunctions
import static io.seqera.wave.util.StringUtils.trunc
/**
 * Models JWT authorization tokens
 * used to connect with Tower service
 */
@Canonical
class JwtAuth {

    /**
     * The target endpoint
     */
    final String endpoint

    /**
     * The auth token as sent in the request
     */
    final String token

    /**
     * The bearer authorization token
     */
    final String bearer

    /**
     * The refresh token to request an updated authorization token
     */
    final String refresh

    /**
     * When this token should expire
     */
    final Instant expiration

    final String key() {
        return DigestFunctions.md5("${endpoint}:${token}")
    }

    JwtAuth withBearer(String value) {
        new JwtAuth(endpoint, token, value, refresh, expiration)
    }

    JwtAuth withRefresh(String value) {
        new JwtAuth(endpoint, token, bearer, value, expiration)
    }

    @Override
    String toString() {
        return "JwtAuth{" +
                "endpoint='" + endpoint + '\'' +
                ", token='" + trunc(token,15) + '\'' +
                ", bearer='" + trunc(bearer,15) + '\'' +
                ", refresh='" + trunc(refresh,15) + '\'' +
                ", expiration=" + expiration +
                '}';
    }

    static JwtAuth of(PlatformId platformId) {
        new JwtAuth(
                platformId.towerEndpoint,
                platformId.accessToken,
                platformId.accessToken,
                platformId.refreshToken,
        )
    }

    static JwtAuth of(ContainerInspectRequest req) {
        new JwtAuth(
                req.towerEndpoint,
                req.towerAccessToken,
                req.towerAccessToken )
    }

    static JwtAuth of(String endpoint, String token, String refresh=null) {
        new JwtAuth(endpoint, token, token, refresh)
    }
}

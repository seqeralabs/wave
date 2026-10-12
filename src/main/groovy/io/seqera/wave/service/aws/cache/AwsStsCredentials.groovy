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

package io.seqera.wave.service.aws.cache

import java.time.Instant

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.seqera.serde.moshi.MoshiSerializable
import software.amazon.awssdk.services.sts.model.Credentials

/**
 * Serializable wrapper for AWS STS temporary credentials, used as a cache value
 * in {@link AwsRoleCache}
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
@EqualsAndHashCode
@ToString(includePackage = false, includeNames = true, excludes = ['secretAccessKey', 'sessionToken'])
class AwsStsCredentials implements MoshiSerializable {
    String accessKeyId
    String secretAccessKey
    String sessionToken
    long expirationEpochMilli

    static AwsStsCredentials from(Credentials creds) {
        new AwsStsCredentials(
                accessKeyId: creds.accessKeyId(),
                secretAccessKey: creds.secretAccessKey(),
                sessionToken: creds.sessionToken(),
                expirationEpochMilli: creds.expiration().toEpochMilli()
        )
    }

    Credentials toSdkCredentials() {
        Credentials.builder()
                .accessKeyId(accessKeyId)
                .secretAccessKey(secretAccessKey)
                .sessionToken(sessionToken)
                .expiration(Instant.ofEpochMilli(expirationEpochMilli))
                .build()
    }

    Instant expiration() {
        Instant.ofEpochMilli(expirationEpochMilli)
    }
}
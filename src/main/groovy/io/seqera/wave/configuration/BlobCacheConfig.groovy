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

package io.seqera.wave.configuration

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
/**
 * Model blob cache settings
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@ToString(includeNames = true, includePackage = false, excludes = 'storageSecretKey', ignoreNulls = true)
@CompileStatic
class BlobCacheConfig {

    @Value('${wave.blobCache.enabled:false}')
    boolean enabled

    @Value('${wave.blobCache.status.delay:5s}')
    Duration statusDelay

    @Value('${wave.blobCache.timeout:5m}')
    Duration transferTimeout

    @Value('${wave.blobCache.status.duration:5d}')
    Duration statusDuration

    @Value('${wave.blobCache.storage.bucket}')
    String storageBucket

    @Nullable
    @Value('${wave.blobCache.storage.endpoint}')
    String storageEndpoint

    @Value('${wave.blobCache.storage.region}')
    String storageRegion

    @Nullable
    @Value('${wave.blobCache.storage.accessKey}')
    String storageAccessKey

    @Nullable
    @Value('${wave.blobCache.storage.secretKey}')
    String storageSecretKey

    @Nullable
    @Value('${wave.blobCache.baseUrl}')
    String baseUrl

    @Value('${wave.blobCache.s5cmdImage}')
    String s5Image

    @Nullable
    @Value('${wave.blobCache.requestsCpu}')
    String requestsCpu

    @Nullable
    @Value('${wave.blobCache.requestsMemory}')
    String requestsMemory

    @Nullable
    @Value('${wave.blobCache.url-signature-duration:30m}')
    Duration urlSignatureDuration

    @Value('${wave.blobCache.backoffLimit:3}')
    Integer backoffLimit

    Map<String,String> getEnvironment() {
        final result = new HashMap<String,String>(10)
        if( storageRegion ) {
            result.put('AWS_REGION', storageRegion)
            result.put('AWS_DEFAULT_REGION', storageRegion)
        }
        if( storageAccessKey ) {
            result.put('AWS_ACCESS_KEY_ID', storageAccessKey)
        }
        if( storageSecretKey ) {
            result.put('AWS_SECRET_ACCESS_KEY', storageSecretKey)
        }
        return result
    }

    String getStorageBucket() {
        if( !storageBucket )
            return null
        return storageBucket.startsWith('s3://')
                ? storageBucket
                : 's3://' + storageBucket
    }
}

package io.seqera.wave.configuration

import java.time.Duration
import javax.annotation.Nullable

import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
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

    @Value('${wave.blobCache.storage.bucket:}')
    String storageBucket

    @Nullable
    @Value('${wave.blobCache.storage.endpoint}')
    String storageEndpoint

    @Nullable
    @Value('${wave.blobCache.storage.region}')
    String storageRegion

    @Nullable
    @Value('${wave.blobCache.storage.accessKey}')
    String storageAccessKey

    @Nullable
    @Value('${wave.blobCache.storage.secretKey}')
    String storageSecretKey

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

}

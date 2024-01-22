package io.seqera.wave.service.blob.signing

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.service.blob.BlobSigningService
import io.seqera.wave.util.BucketTokenizer
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Singleton
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
/**
 * Implements a signed strategy based on AWS s3 pre-signed url
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(property = 'wave.blobCache.signing-strategy', value = 'aws-presigned-url')
@Singleton
@CompileStatic
class S3BlobSigningService implements BlobSigningService {

    @Inject
    private BlobCacheConfig blobConfig

    @Inject
    private S3Presigner presigner

    @PostConstruct
    private void init() {
        log.debug "Creating AWS S3 signing service - config=$blobConfig"
    }

    @Override
    String createSignedUri(String uri) {
        final result = uri ? createPresignedGetUrl(uri) : null
        return result ? unescapeUriPath(result) : null
    }

    /**
     *  Create a pre-signed URL to download an object in a subsequent GET request.
     *  @param s3 bucket name
     *  @param key in the s3 bucket
     *  @return pre signed URL
     */
    private String createPresignedGetUrl(String bucketPath) {
        final parsed = BucketTokenizer.from(bucketPath)
        final objectRequest = (GetObjectRequest) GetObjectRequest.builder()
                .bucket(parsed.bucket)
                .key(parsed.key)
                .build()

        final presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(blobConfig.urlSignatureDuration)
                .getObjectRequest(objectRequest)
                .build()

        final presignedRequest = presigner.presignGetObject(presignRequest);
        return presignedRequest.url().toExternalForm()
    }

    protected String unescapeUriPath(String uri) {
        if( !uri )
            return null
        final p = uri.indexOf('?')
        if( p==-1 )
            return URLDecoder.decode(uri, 'UTF-8')
        final base = uri.substring(0,p)
        return URLDecoder.decode(base, 'UTF-8') + uri.substring(p)
    }
}

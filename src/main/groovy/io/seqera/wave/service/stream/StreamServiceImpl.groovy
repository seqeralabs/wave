package io.seqera.wave.service.stream

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.Charset

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.core.RoutePath
import io.seqera.wave.exception.HttpServerRetryableErrorException
import io.seqera.wave.http.HttpClientFactory
import io.seqera.wave.service.blob.BlobCacheService
import io.seqera.wave.util.StringUtils
import io.seqera.wave.util.ZipUtils
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.apache.commons.io.IOUtils
import static io.seqera.wave.WaveDefault.HTTP_RETRYABLE_ERRORS
/**
 * Implement a service for the streaming of remote resources
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class StreamServiceImpl implements StreamService {

    @Inject
    private RegistryProxyService proxyService

    @Inject
    @Nullable
    private BlobCacheService blobCacheService

    @Override
    InputStream stream(String location) {
        assert location, "Missing 'location' attribute"
        if( location.startsWith("docker://") ) {
            return dockerStream0(RoutePath.parse(location))
        }
        if( location.startsWith("http://") || location.startsWith("https://") ) {
            return httpStream0(location)
        }
        if( location.startsWith('data:')) {
            final byte[] decoded = Base64.getDecoder().decode(location.substring(5))
            return new ByteArrayInputStream(decoded)
        }
        if( location.startsWith('gzip:')) {
            final byte[] decoded = Base64.getDecoder().decode(location.substring(5))
            return ZipUtils.decompress(decoded)
        }
        throw new IllegalArgumentException("Unsupported location protocol: ${StringUtils.trunc(location)}")
    }

    protected InputStream httpStream0(String url) throws IOException, InterruptedException {
        final HttpClient client = HttpClientFactory.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().GET().uri(URI.create(url)).build();
        HttpResponse<InputStream> resp = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if( HTTP_RETRYABLE_ERRORS.contains(resp.statusCode()) ) {
            final String err = IOUtils.toString(resp.body(), Charset.defaultCharset());
            final String msg = String.format("Unexpected server response code %d for request 'GET %s' - message: %s", resp.statusCode(), url, err);
            throw new HttpServerRetryableErrorException(msg);
        }
        return resp.body();
    }

    protected InputStream dockerStream0(RoutePath route) {
        final empty = Map.<String,List<String>>of()
        final resp = proxyService.handleRequest(route, empty)
        // when it's a redirect response, just open a stream from that uri
        if( resp.isRedirect() ) {
            return httpStream0(resp.location)
        }
        // when it's a response with a binary body, just return it
        if( resp.body!=null ) {
            return resp.body
        }
        // otherwise cache the blob and stream the resulting uri
        if( blobCacheService ) {
            final blobCache = blobCacheService .retrieveBlobCache(route, empty)
            if( blobCache.succeeded() ) {
                return httpStream0(blobCache.locationUri)
            }
            final String msg = blobCache.logs ?: "Unable to cache blob ${blobCache.locationUri}"
            throw new IllegalStateException(msg)
        }
        else {
            throw new UnsupportedOperationException("Operation not supported")
        }
    }
}

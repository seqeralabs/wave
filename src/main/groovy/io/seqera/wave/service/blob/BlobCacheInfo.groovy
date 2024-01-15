package io.seqera.wave.service.blob

import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.transform.ToString
/**
 * Model a blob cache metadata entry
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includePackage = false, includeNames = true)
@Canonical
@CompileStatic
class BlobCacheInfo {

    /**
     * The HTTP location from the where the cached container blob can be retrieved
     */
    String locationUri

    /**
     * The request http headers
     */
    final Map<String,String> headers

    /**
     * The instant when the cache request was created
     */
    final Instant creationTime

    /**
     * The instant when the cache request completed
     */
    final Instant completionTime

    /**
     * The exit status of the command carried out to store the container
     * blob in the object storage cache
     */
    final Integer exitStatus

    /**
     * The output logs of the command carried out to store the container
     * blob in the object storage cache
     */
    final String logs

    boolean succeeded() {
        locationUri && exitStatus==0
    }

    boolean done() {
        locationUri && completionTime!=null
    }

    String getContentType() {
        headers?.find(it-> it.key.toLowerCase()=='content-type')?.value
    }

    String getCacheControl() {
        headers?.find(it-> it.key.toLowerCase()=='cache-control')?.value
    }

    static BlobCacheInfo create(Map<String,List<String>> headers) {
        final headers0 = new LinkedHashMap<String,String>()
        for( Map.Entry<String,List<String>> it : headers )
            headers0.put( it.key, it.value.join(',') )
        new BlobCacheInfo(null, headers0, Instant.now())
    }

    static BlobCacheInfo create(String locationUrl, Map<String,List<String>> headers) {
        final headers0 = new LinkedHashMap<String,String>()
        for( Map.Entry<String,List<String>> it : headers )
            headers0.put( it.key, it.value.join(',') )
        new BlobCacheInfo(locationUrl, headers0, Instant.now())
    }


    static BlobCacheInfo create1(String locationUrl, Map<String,String> headers) {
        new BlobCacheInfo(locationUrl, headers, Instant.now())
    }

    BlobCacheInfo cached() {
        new BlobCacheInfo(
                locationUri,
                headers,
                creationTime,
                creationTime,
                0)
    }

    BlobCacheInfo completed(int status, String logs) {
        new BlobCacheInfo(
                locationUri,
                headers,
                creationTime,
                Instant.now(),
                status,
                logs)
    }

    BlobCacheInfo failed(String logs) {
        new BlobCacheInfo(
                locationUri,
                headers,
                creationTime,
                Instant.now(),
                null,
                logs)
    }

    @Memoized
    static BlobCacheInfo unknown() {
        new BlobCacheInfo(null, null, Instant.ofEpochMilli(0), Instant.ofEpochMilli(0), null)
    }

}

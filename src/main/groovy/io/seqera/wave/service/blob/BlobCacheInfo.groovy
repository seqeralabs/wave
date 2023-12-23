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
    final String locationUri

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

    static BlobCacheInfo create(String locationUrl) {
        new BlobCacheInfo(locationUrl, Instant.now())
    }

    BlobCacheInfo cached() {
        new BlobCacheInfo(this.locationUri, this.creationTime, this.creationTime, 0)
    }

    BlobCacheInfo completed(int status, String logs) {
        new BlobCacheInfo(locationUri, creationTime, Instant.now(), status, logs)
    }

    BlobCacheInfo failed(String logs) {
        new BlobCacheInfo(locationUri, creationTime, Instant.now(), null, logs)
    }

    @Memoized
    static BlobCacheInfo unknown() {
        new BlobCacheInfo(null, Instant.ofEpochMilli(0), null)
    }
}

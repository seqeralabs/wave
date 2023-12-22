package io.seqera.wave.service.blob

import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.transform.ToString
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includePackage = false, includeNames = true)
@Canonical
@CompileStatic
class BlobInfo {

    final String locationUrl
    final Instant creationTime
    final Instant completionTime
    final Integer exitStatus
    final String logs

    boolean succeeded() {
        locationUrl && exitStatus==0
    }

    boolean done() {
        locationUrl && completionTime!=null
    }

    static BlobInfo create(String locationUrl) {
        new BlobInfo(locationUrl, Instant.now())
    }

    BlobInfo cached() {
        new BlobInfo(this.locationUrl, this.creationTime, this.creationTime, 0)
    }

    BlobInfo completed(int status, String logs) {
        new BlobInfo(locationUrl, creationTime, Instant.now(), status, logs)
    }

    BlobInfo failed(String logs) {
        new BlobInfo(locationUrl, creationTime, Instant.now(), null, logs)
    }

    @Memoized
    static BlobInfo unknown() {
        new BlobInfo(null, Instant.ofEpochMilli(0), null)
    }
}

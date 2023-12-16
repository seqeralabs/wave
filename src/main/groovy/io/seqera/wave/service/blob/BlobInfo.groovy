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

    final Instant creationTime
    final String locationUri

    @Memoized
    static BlobInfo unknown() {
        new BlobInfo(Instant.ofEpochMilli(0), null)
    }
}

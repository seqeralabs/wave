package io.seqera.wave.exception

import groovy.transform.CompileStatic
import io.seqera.wave.storage.DigestStore

/**
 * Exception throw when a digest checksum is not matching
 * the expected value
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class MismatchChecksumException extends WaveException {
    private DigestStore digestStore

    MismatchChecksumException(String message, DigestStore store) {
        super(message)
        this.digestStore = store
    }

    DigestStore getDigestStore() { digestStore }
}

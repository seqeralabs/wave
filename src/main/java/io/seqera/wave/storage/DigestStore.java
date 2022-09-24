package io.seqera.wave.storage;

import java.io.Serializable;

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public interface DigestStore extends Serializable {

    byte[] getBytes();
    String getMediaType();
    String getDigest();

}

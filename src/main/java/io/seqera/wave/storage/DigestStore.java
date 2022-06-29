package io.seqera.wave.storage;

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public interface DigestStore {

    byte[] getBytes();
    String getMediaType();
    String getDigest();

}

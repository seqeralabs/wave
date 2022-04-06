package io.seqera.storage;

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public interface DigestStore {

    byte[] getBytes();
    String getMediaType();
    String getDigest();

}

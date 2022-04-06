package io.seqera.storage;

import java.io.InputStream;

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public interface DigestStore {

    long getContentLength();
    InputStream getInputStream();
    String getMediaType();
    String getDigest();

}

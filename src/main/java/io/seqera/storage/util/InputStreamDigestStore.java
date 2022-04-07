package io.seqera.storage.util;

import io.seqera.storage.DigestStore;

import java.io.InputStream;

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
public class InputStreamDigestStore implements DigestStore {

    final private InputStream inputStream;
    final private long size;
    final private String mediaType;
    final private String digest;

    public InputStreamDigestStore(InputStream inputStream, String mediaType, String digest, long size) {
        this.inputStream = inputStream;
        this.mediaType = mediaType;
        this.digest = digest;
        this.size = size;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getDigest() {
        return digest;
    }

    @Override
    public long getContentLength() {
        return size;
    }
}

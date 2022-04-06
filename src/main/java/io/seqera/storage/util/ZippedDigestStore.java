package io.seqera.storage.util;

import io.seqera.storage.DigestStore;
import io.seqera.util.ZipUtils;

import java.io.InputStream;
import java.io.Serializable;

/**
 * Implements a digest store compress/decompression on-demand
 * the byte array content to retain as less as possible memory
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ZippedDigestStore implements DigestStore, Serializable {

    final private byte[] bytes;
    final private long size;
    final private String mediaType;
    final private String digest;

    public ZippedDigestStore(byte[] bytes, String mediaType, String digest) {
        this.bytes = ZipUtils.compress(bytes);
        this.mediaType = mediaType;
        this.digest = digest;
        this.size = bytes.length;
    }

    public InputStream getInputStream() {
        return ZipUtils.decompress(bytes);
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

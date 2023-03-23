package io.seqera.wave.storage;

import java.util.Base64;

import io.seqera.wave.util.ZipUtils;

/**
 * Implements a digest store compress/decompression on-demand
 * the byte array content to retain as less as possible memory
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ZippedDigestStore implements DigestStore{

    final private byte[] bytes;
    final private String mediaType;
    final private String digest;

    public ZippedDigestStore(byte[] bytes, String mediaType, String digest) {
        this.bytes = ZipUtils.compress(bytes);
        this.mediaType = mediaType;
        this.digest = digest;
    }

    public byte[] getBytes() {
        return ZipUtils.decompressAsBytes(bytes);
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getDigest() {
        return digest;
    }

    public String toString() {
        return String.format("ZippedDigestStore(mediaType=%s; digest=%s; bytesBase64=%s)", mediaType, digest, new String(Base64.getEncoder().encode(bytes)));
    }
}

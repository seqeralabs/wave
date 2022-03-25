package io.seqera.storage;

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class DigestByteArray {
    private byte[] bytes;
    private String mediaType;
    private String digest;

    public DigestByteArray(byte[] bytes, String mediaType, String digest) {
        this.bytes = bytes;
        this.mediaType = mediaType;
        this.digest = digest;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getDigest() {
        return digest;
    }
}

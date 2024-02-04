package io.seqera.wave.storage;

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class LayerDigestStore implements DigestStore {

    private final String location;
    private final String mediaType;
    private String digest;
    private int size;

    public LayerDigestStore(String location, String mediaType, String digest, int size) {
        this.location = location;
        this.mediaType = mediaType;
        this.digest = digest;
        this.size = size;
    }

    public String getLocation() {
        return location;
    }

    @Override
    public byte[] getBytes() throws InterruptedException {
        throw new UnsupportedOperationException("LayerDigestStore does not allow 'getBytes' operation");
    }

    @Override
    public String getMediaType() {
        return mediaType;
    }

    @Override
    public String getDigest() {
        return digest;
    }

    @Override
    public Integer getSize() {
        return size;
    }

}

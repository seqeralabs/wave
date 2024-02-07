package io.seqera.wave.storage;

/**
 * Implements a digest holding a reference to Docker layer
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class DockerDigestStore implements DigestStore {

    private String location;
    private String mediaType;
    private String digest;
    private Integer size;

    public DockerDigestStore(String location, String mediaType, String digest, Integer size) {
        this.location = location;
        this.mediaType = mediaType;
        this.digest = digest;
        this.size = size;
    }

    @Override
    public byte[] getBytes() throws InterruptedException {
        throw new UnsupportedOperationException("DockerDigestStore does not support 'getBytes' operation");
    }

    public String getLocation() {
        return location;
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

    public String toLogString() {
        return String.format("DockerDigestStore(digest=%s; size=%d; mediaType=%s; location=%s)", digest, size, mediaType, location);
    }
}

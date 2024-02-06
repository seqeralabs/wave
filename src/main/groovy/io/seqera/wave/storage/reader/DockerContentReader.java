package io.seqera.wave.storage.reader;

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class DockerContentReader implements ContentReader {

    private String location;

    public DockerContentReader(String location) {
        this.location = location;
    }

    public String getLocation() {
        return location;
    }

    @Override
    public byte[] readAllBytes() {
        throw new UnsupportedOperationException("DockerContentReader does not support 'readAllBytes' operation");
    }
}

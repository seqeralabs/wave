package io.seqera.wave.storage.reader;

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class LayerContentReader implements ContentReader {

    private String location;

    public LayerContentReader(String location) {
        this.location = location;
    }

    public String getLocation() {
        return location;
    }

    @Override
    public byte[] readAllBytes() {
        throw new UnsupportedOperationException("LayerContentReader does not support 'readAllBytes' operation");
    }
}

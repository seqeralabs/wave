package io.seqera.wave.storage.reader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Read a layer content from the given http(s) url
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class HttpContentReader implements ContentReader {

    final private String url;

    public HttpContentReader(String url) {
        this.url = url;
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        try(InputStream stream = new URL(url).openStream()) {
            return stream.readAllBytes();
        }
    }

    @Override
    public InputStream openStream() throws IOException {
        return new URL(url).openStream();
    }

    @Override
    public String toString() {
        return String.format("HttpContentReader(%s)",url);
    }
}

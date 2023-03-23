package io.seqera.wave.storage.reader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * Read a layer content from the given http(s) url
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class HttpContentReader implements ContentReader{

    final private String url;

    public HttpContentReader(String url) {
        this.url = url;
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        try(InputStream stream = new URL(url).openConnection().getInputStream()) {
            return stream.readAllBytes();
        }
    }

    @Override
    public String toString() {
        return String.format("HttpContentReader(%s)",url);
    }
}

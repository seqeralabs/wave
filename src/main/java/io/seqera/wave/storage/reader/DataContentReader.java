package io.seqera.wave.storage.reader;

import java.util.Base64;

/**
 * Read a layer content from the given http(s) url
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class DataContentReader implements ContentReader{

    final private String data;

    public DataContentReader(String data) {
        this.data = data;
    }

    @Override
    public byte[] readAllBytes() {
        return Base64.getDecoder().decode(data);
    }
}

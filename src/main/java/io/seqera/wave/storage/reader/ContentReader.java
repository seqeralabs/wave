package io.seqera.wave.storage.reader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

/**
 * Generic interface to read layer content
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public interface ContentReader extends Serializable {

    byte[] readAllBytes() throws IOException;

    default InputStream openStream() throws IOException {
        return new ByteArrayInputStream(readAllBytes());
    }

}

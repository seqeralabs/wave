package io.seqera.wave.storage.reader;

import java.io.IOException;
import java.io.Serializable;

/**
 * Generic interface to read layer content
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public interface ContentReader extends Serializable {

    byte[] readAllBytes() throws IOException;

}

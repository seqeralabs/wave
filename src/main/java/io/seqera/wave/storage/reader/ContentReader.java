package io.seqera.wave.storage.reader;

import java.io.IOException;

/**
 * Generic interface to read layer content
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public interface ContentReader {

    byte[] readAllBytes() throws IOException;

}

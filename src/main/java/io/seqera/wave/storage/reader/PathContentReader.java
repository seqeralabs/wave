package io.seqera.wave.storage.reader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


/**
 * Read a layer content from the given file system path
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Deprecated
public class PathContentReader implements ContentReader {

    final private Path path;

    public PathContentReader(Path path) { this.path = path; }

    @Override
    public byte[] readAllBytes() throws IOException {
        return Files.readAllBytes(path);
    }
}

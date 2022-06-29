package io.seqera.wave.storage.reader;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Creates a concrete instance of {@link ContentReader}
 * for the given location string
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ContentReaderFactory {

    public static ContentReader of(String location) {
        if( location==null )
            throw new IllegalArgumentException("Missing content location");
        if( location.startsWith("/") )
            return new PathContentReader(Paths.get(location));
        if( location.startsWith("http://") || location.startsWith("https://"))
            return new HttpContentReader(location);
        throw new IllegalArgumentException("Unsupported content location: " + location);
    }

    public static ContentReader of(Path location) {
        if( location==null )
            throw new IllegalArgumentException("Missing content location");
        return new PathContentReader(location);
    }
}

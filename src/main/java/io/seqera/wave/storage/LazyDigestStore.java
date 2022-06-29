package io.seqera.wave.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Implements a digest store that laods the binary content on-demand
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class LazyDigestStore implements DigestStore{

    final private String mediaType;
    final private String digest;
    final private Path contentLocation;

    LazyDigestStore(Path content, String mediaType, String digest) {
        this.contentLocation = content;
        this.mediaType = mediaType;
        this.digest = digest;
    }

    @Override
    public byte[] getBytes() {
        try {
            return contentLocation!=null ? Files.readAllBytes(contentLocation) : null;
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to load digest content at path: "+contentLocation, e);
        }
    }

    @Override
    public String getMediaType() {
        return mediaType;
    }

    @Override
    public String getDigest() {
        return digest;
    }

}

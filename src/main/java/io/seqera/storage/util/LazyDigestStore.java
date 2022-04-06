package io.seqera.storage.util;

import io.seqera.storage.DigestStore;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Implements a digest store that loads the binary content on-demand
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class LazyDigestStore implements DigestStore, Serializable {

    final private String mediaType;
    final private String digest;
    final private String contentLocation;

    public LazyDigestStore(Path content, String mediaType, String digest) {
        this.contentLocation = content.toFile().getAbsolutePath();
        this.mediaType = mediaType;
        this.digest = digest;
    }

    @Override
    public InputStream getInputStream() {
        try {
            return contentLocation!=null ? new FileInputStream(Path.of(contentLocation).toFile()) : null;
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

    @Override
    public long getContentLength() {
        try {
            return contentLocation!=null ? Files.size(Path.of(contentLocation)) : -1;
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to load digest content at path: "+contentLocation, e);
        }
    }
}

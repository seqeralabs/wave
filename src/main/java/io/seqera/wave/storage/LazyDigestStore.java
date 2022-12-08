package io.seqera.wave.storage;

import java.io.IOException;

import io.seqera.wave.storage.reader.ContentReader;

/**
 * Implements a digest store that loads the binary content on-demand
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class LazyDigestStore implements DigestStore{

    final private String mediaType;
    final private String digest;
    final private ContentReader contentReader;

    public LazyDigestStore(ContentReader content, String mediaType, String digest) {
        this.contentReader = content;
        this.mediaType = mediaType;
        this.digest = digest;
    }

    @Override
    public byte[] getBytes() {
        try {
            return contentReader !=null ? contentReader.readAllBytes() : null;
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to load digest content at path: "+ contentReader, e);
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

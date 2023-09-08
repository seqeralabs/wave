/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

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

    public String toString() {
        return String.format("LazyDigestStore(mediaType=%s; digest=%s; reader=%s)", mediaType, digest, contentReader.toString());
    }
}

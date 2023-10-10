/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
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
    final private Integer size;

    public LazyDigestStore(ContentReader content, String mediaType, String digest, int size) {
        this.contentReader = content;
        this.mediaType = mediaType;
        this.digest = digest;
        this.size = size;
    }

    @Override
    public byte[] getBytes() throws InterruptedException {
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

    public Integer getSize() {
        return size;
    }

    public String toString() {
        return String.format("LazyDigestStore(mediaType=%s; digest=%s; size=%d; reader=%s)", mediaType, digest, size, contentReader.toString());
    }

    @Override
    public String toLogString() {
        return String.format("LazyDigestStore(digest=%s; size=%d; mediaType=%s; reader=%s)", digest, size, mediaType, contentReader.toLogString());
    }
}

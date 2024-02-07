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

import java.util.Base64;

import io.seqera.wave.util.ZipUtils;

/**
 * Implements a digest store compress/decompression on-demand
 * the byte array content to retain as less as possible memory
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ZippedDigestStore implements DigestStore{

    final private byte[] bytes;
    final private String mediaType;
    final private String digest;
    final Integer size;

    static ZippedDigestStore fromCompressed(byte[] data, String mediaType, String digest, int size) {
        return new ZippedDigestStore(data, mediaType, digest, size);
    }

    static ZippedDigestStore fromUncompressed(byte[] data, String mediaType, String digest, int size) {
        return new ZippedDigestStore(ZipUtils.compress(data), mediaType, digest, size);
    }

    private ZippedDigestStore(byte[] data, String mediaType, String digest, int size) {
        this.bytes = data;
        this.mediaType = mediaType;
        this.digest = digest;
        this.size = size;
    }

    public byte[] getBytes() {
        return ZipUtils.decompressAsBytes(bytes);
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getDigest() {
        return digest;
    }

    public Integer getSize() {
        return size;
    }

    public String toString() {
        return String.format("ZippedDigestStore(mediaType=%s; digest=%s; size=%d; bytesBase64=%s)", mediaType, digest, size, new String(Base64.getEncoder().encode(bytes)));
    }

    @Override
    public String toLogString() {
        return String.format("ZippedDigestStore(digest=%s; size=%d; mediaType=%s; bytes=<omitted>)", digest, size, mediaType);
    }
}

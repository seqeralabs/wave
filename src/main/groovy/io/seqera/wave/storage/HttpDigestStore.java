/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

/**
 * Implements a digest store holding a reference to a
 * remote http blob layer
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class HttpDigestStore implements DigestStore {

    private String location;
    private String mediaType;
    private String digest;
    private Integer size;

    public HttpDigestStore(String location, String mediaType, String digest, Integer size) {
        this.location = location;
        this.mediaType = mediaType;
        this.digest = digest;
        this.size = size;
    }

    @Override
    public byte[] getBytes() throws InterruptedException {
        throw new UnsupportedOperationException("HttpDigestStore does not support 'getBytes' operation");
    }

    public String getLocation() {
        return location;
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
    public Integer getSize() {
        return size;
    }

    public String toLogString() {
        return String.format("HttpDigestStore(digest=%s; size=%d; mediaType=%s; location=%s)", digest, size, mediaType, location);
    }

}

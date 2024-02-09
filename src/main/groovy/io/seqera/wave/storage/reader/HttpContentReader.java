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

package io.seqera.wave.storage.reader;

import java.io.IOException;

/**
 * Read a layer content from the given http(s) url
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Deprecated
public class HttpContentReader implements ContentReader {

    final private String url;

    public HttpContentReader(String url) {
        this.url = url;
    }

    @Override
    public byte[] readAllBytes() throws IOException, InterruptedException {
        throw new UnsupportedOperationException("HttpContentReader does not support 'readAllBytes' operation");
    }

    @Deprecated
    public String getUrl() { return url; }

    public String getLocation() {
        return url;
    }

    @Override
    public String toString() {
        return String.format("HttpContentReader(%s)",url);
    }

    @Override
    public String toLogString() {
        return String.format("location=%s", url);
    }
}

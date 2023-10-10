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
import java.util.Base64;

import io.seqera.wave.util.ZipUtils;

/**
 * Implements a {@link ContentReader} that hold data
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class GzipContentReader implements ContentReader {

    final private byte[] data;

    private GzipContentReader(byte[] data) {
        this.data = data;
    }

    @Override
    public byte[] readAllBytes() {
        return ZipUtils.decompressAsBytes(data);
    }

    public static GzipContentReader fromPlainString(String value) throws IOException {
        final byte[] compressed = ZipUtils.compress(value);
        return new GzipContentReader(compressed);
    }

    public static GzipContentReader fromBase64EncodedString(String value) {
        final byte[] decoded = Base64.getDecoder().decode(value);
        return new GzipContentReader(decoded);
    }

    @Override
    public String toString() {
        return String.format("GzipContentReader(%s)",new String(Base64.getEncoder().encode(data)));
    }

    @Override
    public String toLogString() {
        return "gzip=base64+encoded+string";
    }
}

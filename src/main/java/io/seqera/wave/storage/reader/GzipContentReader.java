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

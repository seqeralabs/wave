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
import java.io.InputStream;
import java.net.URL;

/**
 * Read a layer content from the given http(s) url
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class HttpContentReader implements ContentReader {

    final private String url;

    public HttpContentReader(String url) {
        this.url = url;
    }

    @Override
    public byte[] readAllBytes() throws IOException {
        try(InputStream stream = new URL(url).openStream()) {
            return stream.readAllBytes();
        }
    }

    @Override
    public InputStream openStream() throws IOException {
        return new URL(url).openStream();
    }

    public String getUrl() { return url; }

    @Override
    public String toString() {
        return String.format("HttpContentReader(%s)",url);
    }

    @Override
    public String toLogString() {
        return String.format("location=%s", url);
    }
}

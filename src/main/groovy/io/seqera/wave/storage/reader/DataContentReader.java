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

import java.util.Base64;

/**
 * Read a layer content from the given http(s) url
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class DataContentReader implements ContentReader{

    final private String data;

    public DataContentReader(String data) {
        this.data = data;
    }

    @Override
    public byte[] readAllBytes() {
        return Base64.getDecoder().decode(data);
    }

    @Override
    public String toString() {
        return String.format("DataContentReader(%s)",data);
    }

    @Override
    public String toLogString() {
        return "data=base64+encoded+string";
    }
}

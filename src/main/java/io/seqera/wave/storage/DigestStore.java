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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public interface DigestStore extends Serializable {

    byte[] getBytes();
    String getMediaType();
    String getDigest();
    Integer getSize();

    default InputStream openStream() throws IOException {
        return new ByteArrayInputStream(getBytes());
    }
}

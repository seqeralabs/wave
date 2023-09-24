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

import java.nio.file.Paths;

/**
 * Creates a concrete instance of {@link ContentReader}
 * for the given location string
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ContentReaderFactory {

    public static ContentReader of(String location) {
        if( location==null )
            throw new IllegalArgumentException("Missing content location");
        if( location.startsWith("/") )
            return new PathContentReader(Paths.get(location));
        if( location.startsWith("http://") || location.startsWith("https://"))
            return new HttpContentReader(location);
        if( location.startsWith("data:") ) {
            return new DataContentReader(location.substring(5));
        }
        if( location.startsWith("gzip:") ) {
            return GzipContentReader.fromBase64EncodedString(location.substring(5));
        }
        throw new IllegalArgumentException("Unsupported content location: " + location);
    }

}

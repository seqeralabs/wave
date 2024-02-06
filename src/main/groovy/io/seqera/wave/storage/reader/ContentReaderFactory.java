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
        if( location.startsWith("docker://") ) {
            return new DockerContentReader(location);
        }
        throw new IllegalArgumentException("Unsupported content location: " + location);
    }

}

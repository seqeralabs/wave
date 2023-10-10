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
import java.nio.file.Files;
import java.nio.file.Path;


/**
 * Read a layer content from the given file system path
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Deprecated
public class PathContentReader implements ContentReader {

    final private Path path;

    public PathContentReader(Path path) { this.path = path; }

    @Override
    public byte[] readAllBytes() throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public String toLogString() {
        return String.format("path=%s", path.toString());
    }
}

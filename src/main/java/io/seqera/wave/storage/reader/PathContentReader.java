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

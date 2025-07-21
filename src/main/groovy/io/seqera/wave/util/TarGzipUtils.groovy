/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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

package io.seqera.wave.util

import java.util.zip.GZIPInputStream

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
/**
 * Tar and Gzip utilities
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class TarGzipUtils {

    static InputStream untarGzip(InputStream tarGzStream) {
        def gzipIn = new GZIPInputStream(tarGzStream)
        def tarIn = new TarArchiveInputStream(gzipIn)

        def entry
        while ((entry = tarIn.nextEntry) != null) {
            if (!entry.isDirectory()) {
                long size = entry.size
                return new FilterInputStream(tarIn) {
                    long remaining = size

                    @Override
                    int read() {
                        if (remaining-- <= 0) return -1
                        return super.read()
                    }

                    @Override
                    int read(byte[] b, int off, int len) {
                        if (remaining <= 0) return -1
                        int toRead = Math.min(len, (int) remaining)
                        int read = super.read(b, off, toRead)
                        if (read > 0) remaining -= read
                        return read
                    }
                }
            }
        }

        throw new IllegalStateException("No file entry found in tar archive")
    }


}

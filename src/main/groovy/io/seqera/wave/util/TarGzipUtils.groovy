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

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
/**
 * Tar and Gzip utilities
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class TarGzipUtils {

    static byte[] untarGzip(final InputStream is) throws IOException {
        try (GzipCompressorInputStream gzipStream = new GzipCompressorInputStream(is)) {
            byte[] tarContent = untarToByteArray(gzipStream)
            return tarContent;
        }
    }

    private static byte[] untarToByteArray(final InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (TarArchiveInputStream tarInputStream = new TarArchiveInputStream(is)) {
            TarArchiveEntry entry;
            byte[] buffer = new byte[1024];
            int count;
            while ((entry = (TarArchiveEntry) tarInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    while ((count = tarInputStream.read(buffer)) != -1) {
                        baos.write(buffer, 0, count);
                    }
                }
            }
        }
        return baos.toByteArray();
    }

}

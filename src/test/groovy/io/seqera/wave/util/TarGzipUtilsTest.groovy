/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

import spock.lang.Specification

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream

/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class TarGzipUtilsTest extends Specification {

    def 'should extract file from valid tar.gz stream'() {
        given:
        def tarGzBytes = createGzippedTar('file.txt', 'content')
        def inputStream = new ByteArrayInputStream(tarGzBytes)

        when:
        def resultStream = TarGzipUtils.untarGzip(inputStream)
        def extractedContent = resultStream.text

        then:
        extractedContent == 'content'
    }

    def 'should throw exception when tar.gz stream has no file entries'() {
        given:
        def tarGzBytes = createEmptyGzippedTar()
        def inputStream = new ByteArrayInputStream(tarGzBytes)

        when:
        TarGzipUtils.untarGzip(inputStream)

        then:
        thrown(IllegalStateException)
    }

    def 'should throw exception when input stream is not in tar.gz format'() {
        given:
        def invalidBytes = 'not a tar.gz'.bytes
        def inputStream = new ByteArrayInputStream(invalidBytes)

        when:
        TarGzipUtils.untarGzip(inputStream)

        then:
        thrown(IOException)
    }

    static byte[] createGzippedTar(String fileName, String content) {
        def baos = new ByteArrayOutputStream()
        def gzipOut = new GzipCompressorOutputStream(baos)
        def tarOut = new TarArchiveOutputStream(gzipOut)
        def entry = new TarArchiveEntry(fileName)
        entry.size = content.bytes.length
        tarOut.putArchiveEntry(entry)
        tarOut.write(content.bytes)
        tarOut.closeArchiveEntry()
        tarOut.close()
        gzipOut.close()
        baos.toByteArray()
    }

    static byte[] createEmptyGzippedTar() {
        def baos = new ByteArrayOutputStream()
        def gzipOut = new GzipCompressorOutputStream(baos)
        def tarOut = new TarArchiveOutputStream(gzipOut)
        tarOut.close()
        gzipOut.close()
        baos.toByteArray()
    }

}

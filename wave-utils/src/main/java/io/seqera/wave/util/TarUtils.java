/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.wave.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

/**
 * Implements TAR utility methods
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class TarUtils {

    private static final Logger log = LoggerFactory.getLogger(TarUtils.class);

    static public List<Path> untarGzip(final InputStream is, final Path outputDir) throws IOException {
        try (GzipCompressorInputStream stream = new GzipCompressorInputStream(is)) {
            return untar(stream, outputDir);
        }
    }

    static public List<Path> untar(final InputStream is, final Path outputDir) throws IOException {
        final boolean isUnix = "UnixPath".equals(outputDir.getClass().getSimpleName());
        final List<Path> untaredFiles = new LinkedList<>();
        try ( TarArchiveInputStream tarInputStream = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream("tar", is) ) {
            TarArchiveEntry entry;
            while ((entry = (TarArchiveEntry)tarInputStream.getNextEntry()) != null) {
                final Path outputFile = outputDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    if (!Files.exists(outputFile)) {
                        Files.createDirectories(outputFile);
                        if( isUnix )
                            FileUtils.setPermissionsMode( outputFile, entry.getMode() );
                        FileUtils.setLastModified( outputFile, entry.getLastModifiedDate().getTime() );
                    }
                }
                else {
                    log.trace("Tar outputFile={}; mode={}", outputFile, entry.getMode());
                    Files.createDirectories(outputFile.getParent());
                    final OutputStream outputFileStream = Files.newOutputStream(outputFile, CREATE, APPEND);
                    tarInputStream.transferTo(outputFileStream);
                    outputFileStream.close();
                    if( isUnix )
                        FileUtils.setPermissionsMode(outputFile, entry.getMode());
                    FileUtils.setLastModified( outputFile, entry.getLastModifiedDate().getTime() );
                }
                untaredFiles.add(outputFile);
            }
        }
        catch (ArchiveException e) {
            throw new IOException("Unable to create tar input stream", e);
        }

        return untaredFiles;
    }

    static public byte[] uncompress( byte[] bytes ) throws IOException {
        try (GzipCompressorInputStream stream = new GzipCompressorInputStream(new ByteArrayInputStream(bytes))) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            stream.transferTo(buffer);
            return buffer.toByteArray();
        }
    }

}

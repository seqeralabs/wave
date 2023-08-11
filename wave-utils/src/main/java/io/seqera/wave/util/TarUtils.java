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

        final List<Path> untaredFiles = new LinkedList<>();
        try ( TarArchiveInputStream tarInputStream = (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream("tar", is) ) {
            TarArchiveEntry entry;
            while ((entry = (TarArchiveEntry)tarInputStream.getNextEntry()) != null) {
                final Path outputFile = outputDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    if (!Files.exists(outputFile)) {
                        Files.createDirectories(outputFile);
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

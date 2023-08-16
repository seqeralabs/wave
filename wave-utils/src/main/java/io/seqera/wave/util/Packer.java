/*
 * Copyright 2013-2023, Seqera Labs
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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import io.seqera.wave.api.ContainerLayer;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

/**
 * Utility class to create container layer packages
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class Packer {

    /**
     * See {@link TarArchiveEntry#DEFAULT_DIR_MODE}
     */
    private static final int DIR_MODE = 040000;

    /**
     * See {@link TarArchiveEntry#DEFAULT_FILE_MODE}
     */
    private static final int FILE_MODE = 0100000;


    <T extends OutputStream> T makeTar(Path root, List<Path> files, T target) throws IOException {
        final HashMap<String,Path>entries = new HashMap<>();
        for( Path it : files ) {
            final String name = root.relativize(it).toString();
            entries.put(name, it);
        }
        return makeTar(entries, target);
    }

    <T extends OutputStream> T makeTar(Map<String,Path> entries, T target) throws IOException  {
        try ( final TarArchiveOutputStream archive = new TarArchiveOutputStream(target) ) {
            final TreeSet<String> sorted = new TreeSet<>(entries.keySet());
            for (String name : sorted ) {
                final Path targetPath = entries.get(name);
                final BasicFileAttributes attrs = Files.readAttributes(targetPath, BasicFileAttributes.class);
                final TarArchiveEntry entry = new TarArchiveEntry(targetPath, name);
                entry.setIds(0,0);
                entry.setGroupName("root");
                entry.setUserName("root");
                entry.setModTime(attrs.lastModifiedTime());
                entry.setMode(getMode(targetPath));
                // file permissions
                archive.putArchiveEntry(entry);
                if( !Files.isDirectory(targetPath) ) {
                    Files.copy(targetPath, archive);
                }
                archive.closeArchiveEntry();
            }
            archive.finish();
        }

        return target;
    }

    private int getMode(Path path) throws IOException {
        final int mode = Files.isDirectory(path) ? DIR_MODE : FILE_MODE;
        return mode + FileUtils.getPermissionsMode(path);
    }

    protected <T extends OutputStream> T makeGzip(InputStream source, T target) throws IOException {
        try (final OutputStream compressed = new GzipCompressorOutputStream(target)) {
            source.transferTo(compressed);
            compressed.flush();
        }
        return target;
    }

    public ContainerLayer layer(Path root) throws IOException {
        final List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                files.add(file);
                return FileVisitResult.CONTINUE;
            }
        });

        Collections.sort(files);
        return layer(root, files);
    }

    public ContainerLayer layer(Path root, LinkedHashSet<String> ignorePatterns) throws IOException {
        final List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                files.add(file);
                return FileVisitResult.CONTINUE;
            }
        });

        Collections.sort(files);
        return layer(root, files, ignorePatterns);
    }

    public ContainerLayer layer(Path root, List<Path> files) throws IOException {
        final Map<String,Path> entries = new HashMap<>();
        for( Path it : files )
            entries.put(root.relativize(it).toString(), it);
        return layer(entries);
    }

    public ContainerLayer layer(Path root, List<Path> files, LinkedHashSet<String> ignorePatterns) throws IOException {
        final Map<String,Path> entries = new HashMap<>();

        DockerIgnorePathFilter pathFilter = new DockerIgnorePathFilter(ignorePatterns);
        for( Path it : files ){
            Path relative = root.relativize(it);
            if(pathFilter.accept(relative)){
                entries.put(relative.toString(), it);
            }
        }
        return layer(entries);
    }

    public ContainerLayer layer(Map<String,Path> entries) throws IOException {
        final byte[] tar = makeTar(entries, new ByteArrayOutputStream()).toByteArray();
        final String tarDigest = DigestFunctions.digest(tar);
        final ByteArrayOutputStream gzipStream = new ByteArrayOutputStream();
        makeGzip(new ByteArrayInputStream(tar), gzipStream); gzipStream.close();
        final byte[] gzipBytes = gzipStream.toByteArray();
        final int gzipSize = gzipBytes.length;
        final String gzipDigest = DigestFunctions.digest(gzipBytes);
        final String data = "data:" + new String(Base64.getEncoder().encode(gzipBytes));

        return new ContainerLayer(data, gzipDigest, gzipSize, tarDigest);
    }

}

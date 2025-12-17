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

package io.seqera.wave.service.localfs

import java.io.InputStream
import java.io.OutputStream
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.function.Consumer
import java.util.stream.Collectors
import java.util.stream.Stream

import groovy.transform.CompileStatic
import io.micronaut.objectstorage.ObjectStorageEntry
import io.micronaut.objectstorage.ObjectStorageException
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import io.micronaut.objectstorage.response.UploadResponse

/**
 * Custom implementation of {@link ObjectStorageOperations} for local file system storage.
 * This bypasses Micronaut's {@code LocalStorageOperations} which requires bean injection
 * and cannot be instantiated programmatically.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class LocalStorageOperations implements ObjectStorageOperations<Path, Path, Boolean> {

    private final Path basePath

    LocalStorageOperations(Path basePath) {
        this.basePath = basePath
        try {
            Files.createDirectories(basePath)
        }
        catch (IOException e) {
            throw new ObjectStorageException("Error creating base directory: " + basePath, e)
        }
    }

    @Override
    UploadResponse<Path> upload(UploadRequest request) {
        return upload(request, { Path p -> })
    }

    @Override
    UploadResponse<Path> upload(UploadRequest request, Consumer<Path> requestConsumer) {
        final Path file = resolveSafe(basePath, request.getKey())
        try {
            Files.createDirectories(file.getParent())
            try (OutputStream out = Files.newOutputStream(file)) {
                request.getInputStream().transferTo(out)
            }
            requestConsumer.accept(file)
            return UploadResponse.of(request.getKey(), UUID.randomUUID().toString(), file)
        }
        catch (IOException e) {
            throw new ObjectStorageException("Error uploading file: " + file, e)
        }
    }

    @Override
    Optional<LocalStorageEntry> retrieve(String key) {
        final Path file = resolveSafe(basePath, key)
        if (Files.exists(file)) {
            return Optional.of(new LocalStorageEntry(key, file))
        }
        return Optional.empty()
    }

    @Override
    Boolean delete(String key) {
        final Path file = resolveSafe(basePath, key)
        try {
            if (Files.exists(file)) {
                Files.delete(file)
                return true
            }
            return false
        }
        catch (IOException e) {
            throw new ObjectStorageException("Error deleting file: " + file, e)
        }
    }

    @Override
    boolean exists(String key) {
        return Files.exists(resolveSafe(basePath, key))
    }

    @Override
    Set<String> listObjects() {
        try (Stream<Path> stream = Files.find(basePath, Integer.MAX_VALUE, { path, attrs -> attrs.isRegularFile() })) {
            return stream
                    .map { p -> basePath.relativize(p) }
                    .map { p -> p.toString() }
                    .collect(Collectors.toSet())
        }
        catch (IOException e) {
            throw new ObjectStorageException("Error listing objects", e)
        }
    }

    @Override
    void copy(String sourceKey, String destinationKey) {
        final Path source = resolveSafe(basePath, sourceKey)
        final Path dest = resolveSafe(basePath, destinationKey)
        try {
            Files.createDirectories(dest.getParent())
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING)
        }
        catch (IOException e) {
            throw new ObjectStorageException("Error copying file: " + source + " to " + dest, e)
        }
    }

    /**
     * Resolve a key safely within the base path, preventing path traversal attacks.
     *
     * @param parent the base path
     * @param key the key to resolve
     * @return the resolved path
     * @throws IllegalArgumentException if the resolved path lies outside the base path
     */
    private static Path resolveSafe(Path parent, String key) {
        final Path file = parent.resolve(key).normalize()
        if (!file.startsWith(parent)) {
            throw new IllegalArgumentException("Path lies outside the configured bucket")
        }
        return file
    }

    /**
     * Implementation of {@link ObjectStorageEntry} for local file system storage.
     */
    static class LocalStorageEntry implements ObjectStorageEntry<Path> {

        private final String key
        private final Path file

        LocalStorageEntry(String key, Path file) {
            this.key = key
            this.file = file
        }

        @Override
        String getKey() {
            return key
        }

        @Override
        InputStream getInputStream() {
            try {
                return Files.newInputStream(file)
            }
            catch (IOException e) {
                throw new ObjectStorageException("Error opening input stream for file: " + file, e)
            }
        }

        @Override
        Path getNativeEntry() {
            return file
        }

        @Override
        Optional<String> getContentType() {
            return Optional.ofNullable(URLConnection.guessContentTypeFromName(file.getFileName().toString()))
        }

        // toStreamedFile() uses default implementation from ObjectStorageEntry interface
    }
}

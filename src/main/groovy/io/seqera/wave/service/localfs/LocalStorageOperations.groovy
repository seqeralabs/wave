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

    /**
     * The root directory where all objects are stored.
     */
    private final Path basePath

    /**
     * Creates a new {@code LocalStorageOperations} instance with the specified base path.
     * The base directory will be created if it does not exist.
     *
     * @param basePath the root directory for storing objects
     * @throws ObjectStorageException if the base directory cannot be created
     */
    LocalStorageOperations(Path basePath) {
        this.basePath = basePath
        try {
            Files.createDirectories(basePath)
        }
        catch (IOException e) {
            throw new ObjectStorageException("Error creating base directory: " + basePath, e)
        }
    }

    /**
     * Uploads an object to local storage.
     *
     * @param request the upload request containing the key and input stream
     * @return an {@link UploadResponse} containing the key, a generated ETag, and the file path
     * @throws ObjectStorageException if an I/O error occurs during upload
     */
    @Override
    UploadResponse<Path> upload(UploadRequest request) {
        return upload(request, { Path p -> })
    }

    /**
     * Uploads an object to local storage with a custom consumer for post-processing.
     * Parent directories are created automatically if they do not exist.
     *
     * @param request the upload request containing the key and input stream
     * @param requestConsumer a consumer that receives the created file path after upload
     * @return an {@link UploadResponse} containing the key, a generated ETag, and the file path
     * @throws ObjectStorageException if an I/O error occurs during upload
     */
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

    /**
     * Retrieves an object from local storage.
     *
     * @param key the object key (relative path within the base directory)
     * @return an {@link Optional} containing the {@link LocalStorageEntry} if the object exists,
     *         or an empty {@link Optional} if not found
     * @throws IllegalArgumentException if the key resolves to a path outside the base directory
     */
    @Override
    Optional<LocalStorageEntry> retrieve(String key) {
        final Path file = resolveSafe(basePath, key)
        if (Files.exists(file)) {
            return Optional.of(new LocalStorageEntry(key, file))
        }
        return Optional.empty()
    }

    /**
     * Deletes an object from local storage.
     *
     * @param key the object key (relative path within the base directory)
     * @return {@code true} if the object was deleted, {@code false} if it did not exist
     * @throws ObjectStorageException if an I/O error occurs during deletion
     * @throws IllegalArgumentException if the key resolves to a path outside the base directory
     */
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

    /**
     * Checks whether an object exists in local storage.
     *
     * @param key the object key (relative path within the base directory)
     * @return {@code true} if the object exists, {@code false} otherwise
     * @throws IllegalArgumentException if the key resolves to a path outside the base directory
     */
    @Override
    boolean exists(String key) {
        return Files.exists(resolveSafe(basePath, key))
    }

    /**
     * Lists all objects stored in local storage.
     * Recursively traverses all subdirectories and returns the relative paths of all regular files.
     *
     * @return a {@link Set} of object keys (relative paths) for all stored objects
     * @throws ObjectStorageException if an I/O error occurs while listing objects
     */
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

    /**
     * Copies an object within local storage from one key to another.
     * Parent directories for the destination are created automatically if they do not exist.
     * If the destination already exists, it will be replaced.
     *
     * @param sourceKey the key of the source object
     * @param destinationKey the key for the destination object
     * @throws ObjectStorageException if an I/O error occurs during the copy operation
     * @throws IllegalArgumentException if either key resolves to a path outside the base directory
     */
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
     * Wraps a file on the local file system and provides access to its contents and metadata.
     */
    static class LocalStorageEntry implements ObjectStorageEntry<Path> {

        /**
         * The object key (relative path within the storage).
         */
        private final String key

        /**
         * The absolute path to the file on the local file system.
         */
        private final Path file

        /**
         * Creates a new {@code LocalStorageEntry} for the specified key and file path.
         *
         * @param key the object key (relative path within the storage)
         * @param file the absolute path to the file on the local file system
         */
        LocalStorageEntry(String key, Path file) {
            this.key = key
            this.file = file
        }

        /**
         * Returns the object key.
         *
         * @return the object key (relative path within the storage)
         */
        @Override
        String getKey() {
            return key
        }

        /**
         * Opens and returns an input stream for reading the file contents.
         * The caller is responsible for closing the returned stream.
         *
         * @return an {@link InputStream} for reading the file contents
         * @throws ObjectStorageException if the file cannot be opened for reading
         */
        @Override
        InputStream getInputStream() {
            try {
                return Files.newInputStream(file)
            }
            catch (IOException e) {
                throw new ObjectStorageException("Error opening input stream for file: " + file, e)
            }
        }

        /**
         * Returns the native file system path to the stored object.
         *
         * @return the {@link Path} to the file on the local file system
         */
        @Override
        Path getNativeEntry() {
            return file
        }

        /**
         * Attempts to determine the content type of the file based on its name.
         * Uses {@link URLConnection#guessContentTypeFromName(String)} for detection.
         *
         * @return an {@link Optional} containing the MIME type if detected, or empty if unknown
         */
        @Override
        Optional<String> getContentType() {
            return Optional.ofNullable(URLConnection.guessContentTypeFromName(file.getFileName().toString()))
        }
    }
}

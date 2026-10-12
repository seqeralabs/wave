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

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

import io.micronaut.objectstorage.request.UploadRequest

/**
 * Tests for {@link LocalStorageOperations}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class LocalStorageOperationsTest extends Specification {

    @TempDir
    Path tempDir

    def 'should upload and retrieve a file'() {
        given:
        def storage = new LocalStorageOperations(tempDir)
        def content = 'Hello, World!'
        def key = 'test/file.txt'
        def request = UploadRequest.fromBytes(content.bytes, key)

        when:
        def response = storage.upload(request)

        then:
        response.key == key
        response.ETag != null

        when:
        def result = storage.retrieve(key)

        then:
        result.isPresent()
        result.get().key == key
        result.get().inputStream.text == content
    }

    def 'should return empty optional for non-existent key'() {
        given:
        def storage = new LocalStorageOperations(tempDir)

        when:
        def result = storage.retrieve('non-existent-key')

        then:
        !result.isPresent()
    }

    def 'should delete a file'() {
        given:
        def storage = new LocalStorageOperations(tempDir)
        def content = 'Hello, World!'
        def key = 'delete-test.txt'
        def request = UploadRequest.fromBytes(content.bytes, key)
        storage.upload(request)

        expect:
        storage.exists(key)

        when:
        def deleted = storage.delete(key)

        then:
        deleted
        !storage.exists(key)
    }

    def 'should return false when deleting non-existent file'() {
        given:
        def storage = new LocalStorageOperations(tempDir)

        when:
        def deleted = storage.delete('non-existent-key')

        then:
        !deleted
    }

    def 'should check file existence'() {
        given:
        def storage = new LocalStorageOperations(tempDir)
        def key = 'exists-test.txt'
        def request = UploadRequest.fromBytes('content'.bytes, key)

        expect:
        !storage.exists(key)

        when:
        storage.upload(request)

        then:
        storage.exists(key)
    }

    def 'should list all objects'() {
        given:
        def storage = new LocalStorageOperations(tempDir)
        storage.upload(UploadRequest.fromBytes('1'.bytes, 'file1.txt'))
        storage.upload(UploadRequest.fromBytes('2'.bytes, 'dir/file2.txt'))
        storage.upload(UploadRequest.fromBytes('3'.bytes, 'dir/subdir/file3.txt'))

        when:
        def objects = storage.listObjects()

        then:
        objects.size() == 3
        objects.contains('file1.txt')
        objects.contains('dir/file2.txt') || objects.contains('dir' + File.separator + 'file2.txt')
    }

    def 'should copy a file'() {
        given:
        def storage = new LocalStorageOperations(tempDir)
        def content = 'Copy me!'
        def sourceKey = 'source.txt'
        def destKey = 'dest/copied.txt'
        storage.upload(UploadRequest.fromBytes(content.bytes, sourceKey))

        when:
        storage.copy(sourceKey, destKey)

        then:
        storage.exists(sourceKey)
        storage.exists(destKey)
        storage.retrieve(destKey).get().inputStream.text == content
    }

    def 'should reject path traversal attacks'() {
        given:
        def storage = new LocalStorageOperations(tempDir)

        when:
        storage.retrieve('../../../etc/passwd')

        then:
        thrown(IllegalArgumentException)
    }

    def 'should reject path traversal in upload'() {
        given:
        def storage = new LocalStorageOperations(tempDir)
        def request = UploadRequest.fromBytes('malicious'.bytes, '../../../tmp/evil.txt')

        when:
        storage.upload(request)

        then:
        thrown(IllegalArgumentException)
    }

    def 'should convert entry to streamed file'() {
        given:
        def storage = new LocalStorageOperations(tempDir)
        def content = 'Streamed content'
        def key = 'stream-test.txt'
        storage.upload(UploadRequest.fromBytes(content.bytes, key))

        when:
        def entry = storage.retrieve(key).get()
        def streamedFile = entry.toStreamedFile()

        then:
        streamedFile != null
        streamedFile.inputStream.text == content
    }

    def 'should handle nested directories in upload'() {
        given:
        def storage = new LocalStorageOperations(tempDir)
        def content = 'Nested content'
        def key = 'a/b/c/d/nested.txt'
        def request = UploadRequest.fromBytes(content.bytes, key)

        when:
        storage.upload(request)

        then:
        storage.exists(key)
        Files.exists(tempDir.resolve(key))
    }

    def 'should return native entry as Path'() {
        given:
        def storage = new LocalStorageOperations(tempDir)
        def key = 'native-test.txt'
        storage.upload(UploadRequest.fromBytes('content'.bytes, key))

        when:
        def entry = storage.retrieve(key).get()

        then:
        entry.nativeEntry == tempDir.resolve(key)
        entry.nativeEntry instanceof Path
    }
}

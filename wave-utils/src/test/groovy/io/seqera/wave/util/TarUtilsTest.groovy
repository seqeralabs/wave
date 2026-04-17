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

package io.seqera.wave.util

import spock.lang.Specification

import java.nio.file.Files

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class TarUtilsTest extends Specification {

    def 'should uncompress a tar file to the target dir' () {
        given:
        def folder = Files.createTempDirectory('test')
        def source = folder.resolve('source')
        def target1 = folder.resolve('target1')
        def target2 = folder.resolve('target2')
        Files.createDirectory(source)
        Files.createDirectory(target1)
        Files.createDirectory(target2)
        and:
        source.resolve('foo.txt').text  = 'Foo'
        source.resolve('bar.txt').text  = 'Bar'
        FileUtils.setPermissions(source.resolve('bar.txt'), 'rwx------')
        and:
        source.resolve('subdir')
        Files.createDirectory(source.resolve('subdir'))
        source.resolve('subdir/baz.txt').text = 'Baz'
        and:
        FileUtils.setLastModified(source.resolve('foo.txt'), 1_691_100_000)
        FileUtils.setLastModified(source.resolve('bar.txt'), 1_691_200_000)
        FileUtils.setLastModified(source.resolve('subdir/baz.txt'), 1_691_300_000)

        /*
         * should tar file without preserving file timestamps
         */
        when:
        def layer = new Packer().layer(source)
        and:
        def gzip = layer.location.replace('data:','').decodeBase64()
        and:
        TarUtils.untarGzip( new ByteArrayInputStream(gzip), target1)
        then:
        target1.resolve('foo.txt').text == 'Foo'
        target1.resolve('bar.txt').text == 'Bar'
        target1.resolve('subdir/baz.txt').text == 'Baz'
        and:
        FileUtils.getPermissions(target1.resolve('bar.txt')) == 'rwx------'
        and:
        Files.getLastModifiedTime(target1.resolve('foo.txt')).toMillis() == 0
        Files.getLastModifiedTime(target1.resolve('bar.txt')).toMillis() == 0
        Files.getLastModifiedTime(target1.resolve('subdir/baz.txt')).toMillis() == 0


        /*
        * should tar file preserving file timestamps
        */
        when:
        layer = new Packer(preserveFileTimestamp: true).layer(source)
        and:
        gzip = layer.location.replace('data:','').decodeBase64()
        and:
        TarUtils.untarGzip( new ByteArrayInputStream(gzip), target2)
        then:
        target2.resolve('foo.txt').text == 'Foo'
        target2.resolve('bar.txt').text == 'Bar'
        target2.resolve('subdir/baz.txt').text == 'Baz'
        and:
        FileUtils.getPermissions(target2.resolve('bar.txt')) == 'rwx------'
        and:
        Files.getLastModifiedTime(target2.resolve('foo.txt')).toMillis() == 1_691_100_000
        Files.getLastModifiedTime(target2.resolve('bar.txt')).toMillis() == 1_691_200_000
        Files.getLastModifiedTime(target2.resolve('subdir/baz.txt')).toMillis() == 1_691_300_000

        cleanup:
        folder?.deleteDir()
    }

}

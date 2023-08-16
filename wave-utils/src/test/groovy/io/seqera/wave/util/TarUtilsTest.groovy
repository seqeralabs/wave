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
        def target = folder.resolve('target')
        Files.createDirectory(source)
        Files.createDirectory(target)
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

        when:
        def layer = new Packer().layer(source)
        and:
        def gzip = layer.location.replace('data:','').decodeBase64()
        and:
        TarUtils.untarGzip( new ByteArrayInputStream(gzip), target)
        then:
        target.resolve('foo.txt').text == 'Foo'
        target.resolve('bar.txt').text == 'Bar'
        target.resolve('subdir/baz.txt').text == 'Baz'
        and:
        FileUtils.getPermissions(target.resolve('bar.txt')) == 'rwx------'
        and:
        Files.getLastModifiedTime(target.resolve('foo.txt')).toMillis() == 1_691_100_000
        Files.getLastModifiedTime(target.resolve('bar.txt')).toMillis() == 1_691_200_000
        Files.getLastModifiedTime(target.resolve('subdir/baz.txt')).toMillis() ==  1_691_300_000

        cleanup:
        folder?.deleteDir()
    }

}

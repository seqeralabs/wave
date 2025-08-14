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
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.FileTime

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class PackerTest extends Specification {

    def 'should tar bundle' () {
        given:
        def folder = Files.createTempDirectory('test')
        and:
        def result = folder.resolve('result')
        def result2 = folder.resolve('result2')
        and:
        def rootPath = folder.resolve('bundle'); Files.createDirectories(rootPath)
        rootPath.resolve('main.nf').text = "I'm the main file"
        Files.createDirectories(rootPath.resolve('this/that'))
        and:
        Files.write(rootPath.resolve('this/hola.txt'), "Hola".bytes)
        Files.write(rootPath.resolve('this/hello.txt'), "Hello".bytes)
        Files.write(rootPath.resolve('this/that/ciao.txt'), "Ciao".bytes)
        and:
        def files = new ArrayList<Path>()
        files << rootPath.resolve('this')
        files << rootPath.resolve('this/hola.txt')
        files << rootPath.resolve('this/hello.txt')
        files << rootPath.resolve('this/that')
        files << rootPath.resolve('this/that/ciao.txt')
        files << rootPath.resolve('main.nf')
        and:
        for( Path it : files ) {
            final mode = Files.isDirectory(it) ? 0700 : 0600
            FileUtils.setPermissionsMode(it, mode)
        }
        and:
        def packer = new Packer()

        when:
        def buffer = new ByteArrayOutputStream()
        packer.makeTar(rootPath, files, buffer)
        and:
        TarUtils.untar( new ByteArrayInputStream(buffer.toByteArray()), result )
        then:
        result.resolve('main.nf').text == rootPath.resolve('main.nf').text
        result.resolve('this/hola.txt').text == rootPath.resolve('this/hola.txt').text
        result.resolve('this/hello.txt').text == rootPath.resolve('this/hello.txt').text
        result.resolve('this/that/ciao.txt').text == rootPath.resolve('this/that/ciao.txt').text
        and:
        FileUtils.getPermissionsMode(result.resolve('main.nf')) == 0600
        FileUtils.getPermissionsMode(result.resolve('this/hola.txt')) == 0600
        FileUtils.getPermissionsMode(result.resolve('this/that')) == 0700
        and:
        Files.getLastModifiedTime(result.resolve('main.nf')).toMillis() == 0

        when:
        def layer = packer.layer(rootPath, files)
        then:
        layer.tarDigest == 'sha256:f556b94e9b6f5f72b86e44833614b465df9f65cb4210e3f4416292dca1618360'
        layer.gzipDigest == 'sha256:e58685a82452a11faa926843e7861c94bdb93e2c8f098b5c5354ec9b6fee2b68'
        layer.gzipSize == 251
        and:
        def gzip = layer.location.replace('data:','').decodeBase64()
        TarUtils.untarGzip( new ByteArrayInputStream(gzip), result2)
        and:
        result2.resolve('main.nf').text == rootPath.resolve('main.nf').text
        result2.resolve('this/hola.txt').text == rootPath.resolve('this/hola.txt').text
        result2.resolve('this/hello.txt').text == rootPath.resolve('this/hello.txt').text
        result2.resolve('this/that/ciao.txt').text == rootPath.resolve('this/that/ciao.txt').text
        and:
        Files.getLastModifiedTime(result2.resolve('main.nf')).toMillis() == 0

        cleanup:
        folder?.deleteDir()
    }

    def 'should tar bundle and preserve timestamps' () {
        given:
        def LAST_MODIFIED = FileTime.fromMillis(1_000_000_000_000)
        def folder = Files.createTempDirectory('test')
        and:
        def result = folder.resolve('result')
        def result2 = folder.resolve('result2')
        and:
        def rootPath = folder.resolve('bundle'); Files.createDirectories(rootPath)
        rootPath.resolve('main.nf').text = "I'm the main file"
        Files.createDirectories(rootPath.resolve('this/that'))
        and:
        Files.write(rootPath.resolve('this/hola.txt'), "Hola".bytes)
        Files.write(rootPath.resolve('this/hello.txt'), "Hello".bytes)
        Files.write(rootPath.resolve('this/that/ciao.txt'), "Ciao".bytes)
        and:
        def files = new ArrayList<Path>()
        files << rootPath.resolve('this')
        files << rootPath.resolve('this/hola.txt')
        files << rootPath.resolve('this/hello.txt')
        files << rootPath.resolve('this/that')
        files << rootPath.resolve('this/that/ciao.txt')
        files << rootPath.resolve('main.nf')
        and:
        for( Path it : files ) {
            Files.setLastModifiedTime(it, LAST_MODIFIED)
            final mode = Files.isDirectory(it) ? 0700 : 0600
            FileUtils.setPermissionsMode(it, mode)
        }
        and:
        def packer = new Packer(preserveFileTimestamp: true)

        when:
        def buffer = new ByteArrayOutputStream()
        packer.makeTar(rootPath, files, buffer)
        and:
        TarUtils.untar( new ByteArrayInputStream(buffer.toByteArray()), result )
        then:
        result.resolve('main.nf').text == rootPath.resolve('main.nf').text
        result.resolve('this/hola.txt').text == rootPath.resolve('this/hola.txt').text
        result.resolve('this/hello.txt').text == rootPath.resolve('this/hello.txt').text
        result.resolve('this/that/ciao.txt').text == rootPath.resolve('this/that/ciao.txt').text
        and:
        FileUtils.getPermissionsMode(result.resolve('main.nf')) == 0600
        FileUtils.getPermissionsMode(result.resolve('this/hola.txt')) == 0600
        FileUtils.getPermissionsMode(result.resolve('this/that')) == 0700
        and:
        Files.getLastModifiedTime(result.resolve('main.nf')) == LAST_MODIFIED

        when:
        def layer = packer.layer(rootPath, files)
        then:
        layer.tarDigest == 'sha256:81200f6ad32793567d8070375dc51312a1711fedf6a1c6f5e4a97fa3014f3491'
        layer.gzipDigest == 'sha256:09a2deca4293245909223db505cf69affa1a8ff8acb745fe3cad38bc0b719110'
        layer.gzipSize == 254
        and:
        def gzip = layer.location.replace('data:','').decodeBase64()
        TarUtils.untarGzip( new ByteArrayInputStream(gzip), result2)
        and:
        result2.resolve('main.nf').text == rootPath.resolve('main.nf').text
        result2.resolve('this/hola.txt').text == rootPath.resolve('this/hola.txt').text
        result2.resolve('this/hello.txt').text == rootPath.resolve('this/hello.txt').text
        result2.resolve('this/that/ciao.txt').text == rootPath.resolve('this/that/ciao.txt').text

        cleanup:
        folder?.deleteDir()
    }

    def 'should ignore based on ignore patterns' () {
        given:
        def LAST_MODIFIED = FileTime.fromMillis(1_000_000_000_000)
        def folder = Files.createTempDirectory('test')
        and:
        def result = folder.resolve('result')
        and:
        def rootPath = folder.resolve('bundle'); Files.createDirectories(rootPath)
        rootPath.resolve('main.nf').text = "I'm the main file"
        Files.createDirectories(rootPath.resolve('this/that'))
        Files.createDirectories(rootPath.resolve('this/ignore'))
        and:
        Files.write(rootPath.resolve('this/hola.txt'), "Hola".bytes)
        Files.write(rootPath.resolve('this/hello.txt'), "Hello".bytes)
        Files.write(rootPath.resolve('this/that/ciao.txt'), "Ciao".bytes)
        Files.write(rootPath.resolve('this/that/exclude.txt'), "Exclude".bytes)

        and:
        def files = new ArrayList<Path>()
        files << rootPath.resolve('this/ignore')
        files << rootPath.resolve('this/that/exclude.txt')
        files << rootPath.resolve('this')
        files << rootPath.resolve('this/hola.txt')
        files << rootPath.resolve('this/hello.txt')
        files << rootPath.resolve('this/that')
        files << rootPath.resolve('this/that/ciao.txt')
        files << rootPath.resolve('main.nf')
        and:
        for( Path it : files ) {
            Files.setLastModifiedTime(it, LAST_MODIFIED)
            final mode = Files.isDirectory(it) ? 0700 : 0600
            FileUtils.setPermissionsMode(it, mode)
        }
        and:
        List<String> ignorePatterns = new ArrayList<>()
        ignorePatterns.add("*/ignore*");
        ignorePatterns.add("main.??")
        ignorePatterns.add("*/*/exclude*")
        and:
        def packer = new Packer().withFilter(DockerIgnoreFilter.from(ignorePatterns))

        when:
        def layer = packer.layer(rootPath)

        then:
        def gzip = layer.location.replace('data:','').decodeBase64()
        TarUtils.untarGzip( new ByteArrayInputStream(gzip), result)
        and:
        result.resolve('this/hola.txt').text == rootPath.resolve('this/hola.txt').text
        result.resolve('this/hello.txt').text == rootPath.resolve('this/hello.txt').text
        result.resolve('this/that/ciao.txt').text == rootPath.resolve('this/that/ciao.txt').text

        when:
        result.resolve('main.nf').text
        then:
        thrown(NoSuchFileException)

        when:
        result.resolve('this/that/exclude.txt').text
        then:
        thrown(NoSuchFileException)

        when:
        result.resolve('this/ignore').size()
        then:
        thrown(NoSuchFileException)

        cleanup:
        folder?.deleteDir()
    }

    def 'should create tar with file name longer than 100 chars'(){
        given:
        def LAST_MODIFIED = FileTime.fromMillis(1_000_000_000_000)
        def folder = Files.createTempDirectory('test')
        and:
        def rootPath = folder.resolve('bundle'); Files.createDirectories(rootPath)
        Files.createDirectories(rootPath.resolve('this/that'))
        and:
        Files.write(rootPath.resolve('this/that/this_is_a_file_name_that_is_exactly_100_characters_long_and_contains_letters_numbers_and_underscores.txt'), "Ciao".bytes)
        and:
        def files = new ArrayList<Path>()
        files << rootPath.resolve('this')
        files << rootPath.resolve('this/that')
        files << rootPath.resolve('this/that/this_is_a_file_name_that_is_exactly_100_characters_long_and_contains_letters_numbers_and_underscores.txt')
        and:
        for( Path it : files ) {
            Files.setLastModifiedTime(it, LAST_MODIFIED)
            final mode = Files.isDirectory(it) ? 0700 : 0600
            FileUtils.setPermissionsMode(it, mode)
        }
        and:
        def packer = new Packer()

        when:
        def buffer = new ByteArrayOutputStream()
        packer.makeTar(rootPath, files, buffer)

        then:
        noExceptionThrown()
    }
}

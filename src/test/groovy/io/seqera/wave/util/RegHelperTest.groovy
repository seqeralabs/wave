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
import spock.lang.Unroll

import java.util.concurrent.CompletableFuture

import com.google.common.hash.Hashing
import groovy.util.logging.Slf4j
import io.seqera.wave.test.ManifestConst
import io.seqera.wave.tower.client.TowerClient

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Slf4j
class RegHelperTest extends Specification {

    def 'should compute digest' () {
        expect:
        RegHelper.digest(ManifestConst.MANIFEST_LIST_CONTENT) == ManifestConst.MANIFEST_DIGEST
    }

    def 'should encode base32' () {
        expect:
        RegHelper.encodeBase32('Simple Solution') == 'knuw24dmmuqfg33mov2gs33o'
        RegHelper.encodeBase32('Hello world') == 'jbswy3dpeb3w64tmmq______'
        RegHelper.encodeBase32('Hello world', false) == 'jbswy3dpeb3w64tmmq'
    }

    def 'should decode base32' () {
        expect:
        RegHelper.decodeBase32('knuw24dmmuqfg33mov2gs33o') == 'Simple Solution'
        RegHelper.decodeBase32('jbswy3dpeb3w64tmmq______') == 'Hello world'
        RegHelper.decodeBase32('jbswy3dpeb3w64tmmq') == 'Hello world'
    }

    def 'should validate encode no padding' () {
        given:
        List<String> words = []
        List<String> encoded = []
        and:
        for( int x : 0..100 ) {
            def w = RegHelper.randomString(5,20)
            words.add(w)
            encoded.add( RegHelper.encodeBase32(w, false) )
        }

        when:
        def decoded = []
        for( int x : 0..100 ) {
            decoded.add( RegHelper.decodeBase32(encoded[x]) )
        }
        then:
        words == decoded
    }


    def 'should generate random hex' () {
        expect:
        RegHelper.random256Hex().length() == 64
    }

    def 'should dump headers' () {
        expect:
        RegHelper.dumpHeaders([
                'key1': ['val1', 'val2'],
                'key2': ['val3']
        ]).trim() == """\
          key1=val1
          key1=val2
          key2=val3
        """.stripIndent().trim()
    }

    def 'should validate digest generation' () {
        given:
        def futures = new ArrayList<>()

        when:
        for( int i=0; i< 20; i++ )  {
            def fut = CompletableFuture.supplyAsync {
                log.debug "Check starting"
                def times = new Random().nextInt(20)
                def len = new Random().nextInt(100,1_000)
                def TEXT = RegHelper.random256Hex() * len * times
                def digest = RegHelper.digest(TEXT)

                when:
                def buffer = ZipUtils.compress(TEXT)
                then:
                def c1 = ZipUtils.decompressAsString(buffer)
                def c2 = ZipUtils.decompressAsBytes(buffer)
                and:
                assert c1 == TEXT
                assert RegHelper.digest(c1) == digest
                and:
                assert c2 == TEXT.bytes
                assert RegHelper.digest(new String(c2)) == digest
                log.debug "Check done"
            }
            futures.add(fut)
        }

        and:
        CompletableFuture.allOf(futures as CompletableFuture[]).get()

        then:
        true
    }

    @Unroll
    def 'should parse docker from statement #LINE' () {
        expect:
        RegHelper.parseFromStatement(LINE) == EXPECT

        where:
        LINE                                | EXPECT
        null                                | null
        'FROM foo'                          | 'foo'
        'FROM  foo '                        | 'foo'
        'FROM --platform=xyz foo'           | 'foo'
        'FROM --platform=xyz foo as this'   | 'foo'
        and:
        // allow singularity file syntax
        'From: foo'                         | 'foo'
        'from: foo'                         | 'foo'
        'from foo'                          | null
    }

    def 'should parse docker entrypoint'() {
        expect:
        RegHelper.parseEntrypoint(LINE) == EXPECTED

        where:
        LINE                                    | EXPECTED
        null                                    | null
        'foo'                                   | null
        and:
        'ENTRYPOINT this --that'                | ['this', '--that']
        'ENTRYPOINT  this --that '              | ['this', '--that']
        and:
        'ENTRYPOINT ["this", "--that"]'         | ['this', '--that']
        'ENTRYPOINT  [ "this" ,  "--that" ] '   | ['this', '--that']
        and:
        'ENTRYPOINT ["this", "a b c"]'          | ['this', 'a b c']

    }

    def 'should create singularity remote yaml file' () {

        when:
        def ret = RegHelper.singularityRemoteFile('oras://quay.io/user/foo:latest')
        then:
        ret == '''\
            Active: SylabsCloud
            Remotes:
              SylabsCloud:
                URI: cloud.sylabs.io
                System: true
                Exclusive: false
            Credentials:
            - URI: oras://quay.io
              Insecure: false
            '''.stripIndent(true)
    }

    def 'should compute sip hash' () {
        given:
        def map = [foo: 'one', bar: 'two']

        expect:
        RegHelper.sipHash(map) == '28e758ec870964c8'
        and:
        RegHelper.sipHash(map) == Hashing
                .sipHash24()
                .newHasher()
                    .putUnencodedChars('foo')
                        .putUnencodedChars(Character.toString(0x1C))
                        .putUnencodedChars('one')
                        .putUnencodedChars(Character.toString(0x1E))
                    .putUnencodedChars('bar')
                        .putUnencodedChars(Character.toString(0x1C))
                        .putUnencodedChars('two')
                        .putUnencodedChars(Character.toString(0x1E))
                .hash()
                .toString()
    }


    def 'should create consistent hash for open array' () {
        given:
        def client = new TowerClient()

        expect:
        RegHelper.sipHash('a') == 'bcf5c2d233d23f0f'
        and:
        RegHelper.sipHash('a') == RegHelper.sipHash('a')
        and:
        RegHelper.sipHash('a','b','c') == RegHelper.sipHash('a','b','c')
        and:
        RegHelper.sipHash('a','b',null) == RegHelper.sipHash('a','b',null)
        and:
        RegHelper.sipHash(new URI('http://foo.com')) == RegHelper.sipHash('http://foo.com')
        and:
        RegHelper.sipHash(100l) == RegHelper.sipHash('100')
    }
}

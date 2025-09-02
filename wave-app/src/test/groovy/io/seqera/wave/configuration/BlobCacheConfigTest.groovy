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

package io.seqera.wave.configuration

import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BlobCacheConfigTest extends Specification {

    def 'should get config env' () {
        when:
        def config = new BlobCacheConfig()
        then:
        config.getEnvironment() == [:]

        when:
        config = new BlobCacheConfig(storageAccessKey: 'xyz', storageSecretKey: 'secret', storageRegion: 'foo')
        then:
        config.getEnvironment() == [
                AWS_REGION: 'foo',
                AWS_DEFAULT_REGION: 'foo',
                AWS_ACCESS_KEY_ID: 'xyz',
                AWS_SECRET_ACCESS_KEY: 'secret'
        ]
    }

    def 'should get bucket name' (){
        when:
        def config = new BlobCacheConfig(storageBucket: BUCKET)
        then:
        config.storageBucket == EXPECTED

        where:
        BUCKET                  | EXPECTED
        null                    | null
        'foo'                   | 's3://foo'
        's3://foo'              | 's3://foo'
        's3://foo/bar'          | 's3://foo/bar'
    }
}

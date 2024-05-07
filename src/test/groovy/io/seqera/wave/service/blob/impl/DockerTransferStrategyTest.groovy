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

package io.seqera.wave.service.blob.impl

import spock.lang.Specification

import io.seqera.wave.configuration.BlobCacheConfig

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class DockerTransferStrategyTest extends Specification {

    def 'should create transfer cli' () {
        given:
        def config = new BlobCacheConfig(
                storageBucket: 's3://foo',
                storageEndpoint: 'https://foo.com',
                storageRegion: 'some-region',
                storageAccessKey: 'xyz',
                storageSecretKey: 'secret',
                s5Image: 'cr.seqera.io/public/s5cmd:latest'
        )
        def strategy = new DockerTransferStrategy(blobConfig: config)

        when:
        def result = strategy.createProcess(['s5cmd', 'run', '--this'])

        then:
        result.command() == [
                'docker', 
                'run',
                '-e', 'AWS_ACCESS_KEY_ID',
                '-e', 'AWS_SECRET_ACCESS_KEY',
                'cr.seqera.io/public/s5cmd:latest',
                's5cmd', 'run', '--this']
        and:
        def env = result.environment()
        env.AWS_REGION == 'some-region'
        env.AWS_DEFAULT_REGION == 'some-region'
        env.AWS_ACCESS_KEY_ID == 'xyz'
        env.AWS_SECRET_ACCESS_KEY == 'secret'
        and:
        result.redirectErrorStream()
    }
}

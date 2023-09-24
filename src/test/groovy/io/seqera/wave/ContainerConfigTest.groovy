/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

package io.seqera.wave

import spock.lang.Specification

import io.seqera.wave.util.ContainerConfigFactory
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ContainerConfigTest extends Specification {

    def 'should deserialize layer config' () {
        given:
        def CONFIG='''
            {
              "entrypoint": ["foo", "bar"],
              "workingDir": "/some/path",
              "layers": [{
                "location": "/some/path/layer.tag.gzip",
                "gzipDigest": "sha256:xxx",
                "gzipSize": 10167366,
                "tarDigest": "sha256:zzz" 
              }] 
            }
            '''
        when:
        def config = ContainerConfigFactory.instance.from(CONFIG)

        then:
        config.workingDir == "/some/path"
        config.entrypoint == ["foo", "bar"]
        and:
        config.layers.first().gzipDigest ==  "sha256:xxx"
        config.layers.first().tarDigest ==  "sha256:zzz"
        config.layers.first().gzipSize == 10167366

    }


    def 'should deserialize layer config with rel path' () {
        given:
        def CONFIG='''
            {
              "entrypoint": ["foo", "bar"],
              "workingDir": "/some/path",
              "layers": [{
                "location": "layer.tag.gzip",
                "gzipDigest": "sha256:xxx",
                "gzipSize": 10167366,
                "tarDigest": "sha256:zzz" 
              }] 
            }
            '''
        when:
        def config = ContainerConfigFactory.instance.from(CONFIG)

        then:
        config.workingDir == "/some/path"
        config.entrypoint == ["foo", "bar"]

    }
}

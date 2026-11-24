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

package io.seqera.wave.service.persistence

import spock.lang.Specification

import java.nio.file.Path
import java.time.Instant

import io.seqera.serde.moshi.MoshiEncodeStrategy
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.mirror.MirrorEntry
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.service.persistence.postgres.Mapper
import io.seqera.wave.service.request.ContainerRequest
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import io.seqera.wave.util.JacksonHelper

/**
 * The user name, email and request IP are kept in the database but never returned by the API
 */
class PersistOnlyFieldsTest extends Specification {

    def 'should store the build user name, email and request ip but not return them' () {
        given:
        def record = new WaveBuildRecord(
                buildId: 'bd-1_1',
                startTime: Instant.now(),
                userName: 'foo-user',
                userEmail: 'foo@bar.com',
                userId: 100,
                requestIp: '10.20.30.40' )

        when:
        def stored = Mapper.fromJson(WaveBuildRecord, Mapper.toJson(record))
        def returned = JacksonHelper.toJson(record)

        then:
        stored.userName == 'foo-user'
        stored.userEmail == 'foo@bar.com'
        stored.requestIp == '10.20.30.40'
        stored.userId == 100
        and:
        returned.contains('"userId":100')
        !returned.contains('userName')
        !returned.contains('userEmail')
        !returned.contains('requestIp')
    }

    def 'should store the mirror user name and email but not return them' () {
        given:
        def request = MirrorRequest.create(
                'docker.io/foo',
                'quay.io/foo',
                'sha256:abc',
                ContainerPlatform.DEFAULT,
                Path.of('/some/path'),
                '{"config":"json"}',
                'sc-1234',
                Instant.now(),
                'GMT',
                new PlatformId(new User(id: 100, userName: 'foo-user', email: 'foo@bar.com'), 200L, 'tok', 'https://api.cloud.seqera.io') )
        def result = MirrorResult.of(request)

        when:
        def stored = Mapper.fromJson(MirrorResult, Mapper.toJson(result), [mirrorId: result.mirrorId])
        def returned = JacksonHelper.toJson(result)
        // the Redis state store is encoded with Moshi, which ignores the Jackson annotations
        def encoder = new MoshiEncodeStrategy<MirrorEntry>() {}
        def cached = encoder.decode(encoder.encode(MirrorEntry.of(request)))

        then:
        stored.userName == 'foo-user'
        stored.userEmail == 'foo@bar.com'
        cached.result.userName == 'foo-user'
        cached.result.userEmail == 'foo@bar.com'
        and:
        returned.contains('"userId":100')
        !returned.contains('userName')
        !returned.contains('userEmail')
    }

    def 'should store the container request ip but not return it' () {
        given:
        def request = new SubmitContainerTokenRequest(
                towerEndpoint: 'https://tower.nf',
                containerImage: 'hello-world',
                containerPlatform: ContainerPlatform.DEFAULT.toString(),
                timestamp: Instant.now().toString() )
        def data = ContainerRequest.of(
                requestId: 'cr-12345',
                identity: new PlatformId(new User(id: 1), 100),
                containerImage: 'docker.io/library/hello-world:latest' )
        def record = new WaveContainerRecord(request, data, 'wave.io/wt/cr-12345/library/hello-world:latest', '10.20.30.40', Instant.now())

        when:
        def stored = Mapper.fromJson(WaveContainerRecord, Mapper.toJson(record))
        def returned = JacksonHelper.toJson(record.withUserIdOnly())

        then:
        stored.ipAddress == '10.20.30.40'
        and:
        !returned.contains('ipAddress')
        !returned.contains('10.20.30.40')
    }

}

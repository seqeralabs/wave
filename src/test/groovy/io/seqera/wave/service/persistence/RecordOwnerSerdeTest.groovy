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

import java.time.Instant

import io.seqera.serde.moshi.MoshiEncodeStrategy
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.mirror.MirrorEntry
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.service.scan.ScanEntry
import io.seqera.wave.service.scan.ScanRequest
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import io.seqera.wave.service.persistence.postgres.Mapper
import io.seqera.wave.tower.RecordOwner
import io.seqera.wave.util.JacksonHelper

/**
 * The owner is a new key inside the existing {@code data} JSONB column, so records written
 * before it existed have to keep loading, as public.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class RecordOwnerSerdeTest extends Specification {

    def 'should load records written before the owner existed' () {
        expect:
        Mapper.fromJson(TYPE, JSON).owner == null

        where:
        TYPE                | JSON
        WaveContainerRecord | '{"id":"wave-123","containerImage":"docker.io/foo:latest","workspaceId":10,"towerEndpoint":"https://api.cloud.seqera.io"}'
        WaveBuildRecord     | '{"buildId":"bd-123_1","targetImage":"docker.io/foo:latest","userId":10,"userEmail":"foo@bar.com"}'
        MirrorResult        | '{"mirrorId":"mr-123","sourceImage":"docker.io/foo","targetImage":"quay.io/foo","userId":10}'
        WaveScanRecord      | '{"id":"sc-123","buildId":"bd-123_1","containerImage":"docker.io/foo:latest","status":"SUCCEEDED"}'
    }

    def 'should round-trip the owner through the data column' () {
        given:
        def owner = new RecordOwner(
                towerEndpoint: 'https://api.cloud.seqera.io',
                userId: 100L,
                workspaceId: 200L )

        when:
        def json = Mapper.toJson(new WaveBuildRecord(buildId: 'bd-1_1', startTime: Instant.now(), owner: owner))
        def record = Mapper.fromJson(WaveBuildRecord, json)

        then:
        record.owner == owner
        and:
        record.owner.towerEndpoint == 'https://api.cloud.seqera.io'
        record.owner.userId == 100L
        record.owner.workspaceId == 200L
    }

    def 'should round-trip the scan owner through the Redis state store' () {
        given:
        // the same encode strategy as ScanStateStore
        def encoder = new MoshiEncodeStrategy<ScanEntry>() {}
        def entry = ScanEntry.create(ScanRequest.of(
                scanId: 'sc-1234',
                buildId: 'bd-1234_1',
                targetImage: 'docker.io/foo:latest',
                platform: ContainerPlatform.DEFAULT,
                workDir: java.nio.file.Path.of('/some/path'),
                creationTime: Instant.now(),
                identity: new PlatformId(new User(id: 100), 200L, 'tok', 'https://api.cloud.seqera.io') ))

        when:
        def restored = encoder.decode(encoder.encode(entry))

        then:
        restored.owner == entry.owner
        restored.owner.userId == 100L
        restored.owner.workspaceId == 200L
        restored.owner.towerEndpoint == 'https://api.cloud.seqera.io'
    }

    def 'should round-trip the mirror owner through the Redis state store' () {
        given:
        // the same encode strategy as MirrorStateStore
        def encoder = new MoshiEncodeStrategy<MirrorEntry>() {}
        def request = MirrorRequest.create(
                'docker.io/foo',
                'quay.io/foo',
                'sha256:abc',
                ContainerPlatform.DEFAULT,
                java.nio.file.Path.of('/some/path'),
                '{"config":"json"}',
                'sc-1234',
                Instant.now(),
                'GMT',
                new PlatformId(new User(id: 100), 200L, 'tok', 'https://api.cloud.seqera.io') )
        def entry = MirrorEntry.of(request)

        when:
        def restored = encoder.decode(encoder.encode(entry))

        then:
        restored.result.owner == entry.result.owner
        restored.result.owner.userId == 100L
        restored.result.owner.workspaceId == 200L
        restored.result.owner.towerEndpoint == 'https://api.cloud.seqera.io'
    }

    def 'should decode a state-store entry written before the owner existed' () {
        given:
        def encoder = new MoshiEncodeStrategy<ScanEntry>() {}
        def json = '{"scanId":"sc-1234","buildId":"bd-1234_1","containerImage":"docker.io/foo:latest","status":"PENDING"}'

        when:
        def restored = encoder.decode(json)

        then:
        restored.scanId == 'sc-1234'
        restored.owner == null
    }

    def 'should keep the owner in the database but out of API responses' () {
        given:
        def owner = new RecordOwner(towerEndpoint: 'https://foo.com', userId: 100L, workspaceId: 200L)
        def record = new WaveBuildRecord(buildId: 'bd-1_1', startTime: Instant.now(), owner: owner)

        when:
        // Mapper is the data column; JacksonHelper stands in for any other mapper, which is
        // what the read endpoints serialise with
        def stored = Mapper.toJson(record)
        def returned = JacksonHelper.toJson(record)

        then:
        stored.contains('"owner"')
        Mapper.fromJson(WaveBuildRecord, stored).owner == owner
        and:
        !returned.contains('"owner"')
        !returned.contains('towerEndpoint')
    }

    def 'should load records written before visibility existed as public' () {
        expect:
        // null visibility means public - the default must survive a record that predates the field
        Mapper.fromJson(WaveBuildRecord, '{"buildId":"bd-1_1"}').visibility == null
        Mapper.fromJson(WaveScanRecord, '{"id":"sc-1"}').visibility == null
        Mapper.fromJson(MirrorResult, '{"mirrorId":"mr-1"}').visibility == null
        Mapper.fromJson(WaveContainerRecord, '{"id":"wave-1"}').visibility == null
    }

    def 'should round-trip visibility through both stores' () {
        given:
        def record = new WaveBuildRecord(buildId: 'bd-1_1', startTime: Instant.now(), visibility: 'private')

        expect: 'the data column'
        Mapper.fromJson(WaveBuildRecord, Mapper.toJson(record)).visibility == 'private'

        and: 'and the Redis state store, where a scan carries its parent visibility'
        def encoder = new MoshiEncodeStrategy<ScanEntry>() {}
        def entry = ScanEntry.create(ScanRequest.of(
                scanId: 'sc-1', buildId: 'bd-1_1', targetImage: 'docker.io/foo:latest',
                platform: ContainerPlatform.DEFAULT, workDir: java.nio.file.Path.of('/some/path'),
                creationTime: Instant.now(), identity: PlatformId.NULL, visibility: 'private' ))
        encoder.decode(encoder.encode(entry)).visibility == 'private'
        and: 'unlike the owner, visibility is returned by the API'
        JacksonHelper.toJson(record).contains('"visibility"')
    }

    def 'should omit the workspace id when there is none' () {
        when:
        def json = Mapper.toJson(new RecordOwner(towerEndpoint: 'https://foo.com', userId: 1L))

        then:
        // null fields are left out of the JSON entirely
        !json.contains('"workspaceId"')
    }

}

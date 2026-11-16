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
import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.service.scan.ScanEntry
import io.seqera.wave.service.scan.ScanRequest
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import io.seqera.wave.service.persistence.postgres.Mapper
import io.seqera.wave.tower.RecordOwner

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

    def 'should omit the owner from the data column when there is none' () {
        when:
        def json = Mapper.toJson(new RecordOwner(towerEndpoint: 'https://foo.com', userId: 1L))

        then:
        // workspaceId is absent rather than null, so a personal-workspace owner stays
        // distinguishable from one whose workspace was never resolved
        !json.contains('"workspaceId"')
    }

}

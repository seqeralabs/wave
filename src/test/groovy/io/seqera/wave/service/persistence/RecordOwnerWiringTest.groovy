/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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
import java.time.Duration
import java.time.Instant

import io.seqera.wave.api.BuildCompression
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildFormat
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.mirror.MirrorResult
import io.seqera.wave.service.request.ContainerRequest
import io.seqera.wave.service.scan.ScanEntry
import io.seqera.wave.service.scan.ScanRequest
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.RecordOwner
import io.seqera.wave.tower.User

/**
 * Each record takes its owner from the identity of the request that created it.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class RecordOwnerWiringTest extends Specification {

    static final PlatformId AUTHENTICATED =
            new PlatformId(new User(id: 100), 200L, 'tok', 'https://api.cloud.seqera.io/')

    // the endpoint is stored exactly as the request carried it, trailing slash included
    static final RecordOwner OWNER = new RecordOwner(
            towerEndpoint: 'https://api.cloud.seqera.io/', userId: 100L, workspaceId: 200L )

    private static BuildRequest buildRequest(PlatformId identity) {
        BuildRequest.of(
                containerId: 'container1234',
                containerFile: 'FROM foo:latest',
                workspace: Path.of('/some/path'),
                targetImage: 'docker.io/my/repo:container1234',
                identity: identity,
                platform: ContainerPlatform.of('amd64'),
                cacheRepository: 'docker.io/my/cache',
                ip: '1.2.3.4',
                configJson: '{"config":"json"}',
                format: BuildFormat.DOCKER,
                maxDuration: Duration.ofMinutes(1),
                compression: BuildCompression.gzip )
    }

    private static WaveBuildRecord buildRecord(PlatformId identity) {
        final request = buildRequest(identity)
        final result = new BuildResult(request.buildId, 0, 'ok', Instant.now(), Duration.ofSeconds(3), null)
        return WaveBuildRecord.fromEvent(new BuildEvent(request, result))
    }

    private static MirrorResult mirrorResult(PlatformId identity) {
        return MirrorResult.of(MirrorRequest.create(
                'docker.io/foo',
                'quay.io/foo',
                'sha256:abc',
                ContainerPlatform.DEFAULT,
                Path.of('/some/path'),
                '{"config":"json"}',
                'sc-1234',
                Instant.now(),
                'GMT',
                identity ))
    }

    private static ScanRequest scanRequest(PlatformId identity) {
        return ScanRequest.of(
                scanId: 'sc-1234',
                buildId: 'bd-1234_1',
                targetImage: 'docker.io/foo:latest',
                platform: ContainerPlatform.DEFAULT,
                workDir: Path.of('/some/path'),
                configJson: '{"config":"json"}',
                creationTime: Instant.now(),
                identity: identity )
    }

    def 'should take the owner from the build request identity' () {
        when:
        def record = buildRecord(AUTHENTICATED)

        then:
        record.owner == OWNER
    }

    def 'should leave an anonymous build record unowned' () {
        expect:
        buildRecord(PlatformId.NULL).owner == null
    }

    def 'should take the owner from the mirror request identity and keep it on completion' () {
        given:
        def pending = mirrorResult(AUTHENTICATED)

        expect:
        pending.owner == OWNER
        and:
        pending.complete(0, 'all good').owner == OWNER
    }

    def 'should carry the scan request identity to the scan record' () {
        when:
        def entry = ScanEntry.create(scanRequest(AUTHENTICATED))

        then:
        entry.owner == OWNER
        and:
        new WaveScanRecord(entry).owner == OWNER
    }

    def 'should keep the scan owner across the terminal states' () {
        given:
        def request = scanRequest(AUTHENTICATED)
        def entry = ScanEntry.create(request)

        expect:
        entry.success([]).owner == entry.owner
        entry.failure(1, 'boom').owner == entry.owner
        ScanEntry.failure(request).owner == entry.owner
    }

    def 'should leave an anonymous scan unowned' () {
        expect:
        ScanEntry.create(scanRequest(PlatformId.NULL)).owner == null
    }

    def 'should take the owner from the container request identity' () {
        given:
        def req = new SubmitContainerTokenRequest(
                towerEndpoint: 'https://api.cloud.seqera.io/',
                towerWorkspaceId: 200,
                containerPlatform: ContainerPlatform.of('amd64'),
                timestamp: Instant.now().toString() )
        def data = ContainerRequest.of(
                requestId: 'wave-1234',
                identity: AUTHENTICATED,
                containerImage: 'hello-world' )

        when:
        def record = new WaveContainerRecord(req, data, 'wave.io/foo:latest', '1.2.3.4', Instant.now())

        then:
        record.owner == OWNER
        and:
        // the record's own field and the owner agree: both are what the pairing lookup keys on
        record.towerEndpoint == 'https://api.cloud.seqera.io/'
    }

    def 'should keep the owner across every container record copy' () {
        given:
        def req = new SubmitContainerTokenRequest(
                towerEndpoint: 'https://api.cloud.seqera.io/',
                towerWorkspaceId: 200,
                containerPlatform: ContainerPlatform.of('amd64'),
                timestamp: Instant.now().toString() )
        def data = ContainerRequest.of(
                requestId: 'wave-1234',
                identity: AUTHENTICATED,
                containerImage: 'hello-world' )
        def record = new WaveContainerRecord(req, data, 'wave.io/foo:latest', '1.2.3.4', Instant.now())

        expect:
        // every copy constructor has to carry it: a record that loses its owner on one path
        // is readable on that path and not on the others
        new WaveContainerRecord(record, 'sha256:src', 'sha256:wave').owner == OWNER
        and:
        record.withUserIdOnly().owner == OWNER
    }

    def 'should leave an anonymous container record unowned' () {
        given:
        def req = new SubmitContainerTokenRequest(
                containerPlatform: ContainerPlatform.of('amd64'),
                timestamp: Instant.now().toString() )
        def data = ContainerRequest.of(
                requestId: 'wave-1234',
                identity: PlatformId.NULL,
                containerImage: 'hello-world' )

        expect:
        new WaveContainerRecord(req, data, 'wave.io/foo:latest', '1.2.3.4', Instant.now()).owner == null
    }

}

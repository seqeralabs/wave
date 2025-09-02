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

package io.seqera.wave.service.job

import spock.lang.Specification

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.configuration.MirrorConfig
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.scan.ScanRequest
import io.seqera.wave.tower.PlatformId

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class JobFactoryTest extends Specification {

    def 'should create job id' () {
        given:
        def ts = Instant.parse('2024-08-18T19:23:33.650722Z')
        def factory = new JobFactory()
        and:
        def request = new BuildRequest(
                containerId: '12345',
                targetImage: 'docker.io/foo:bar',
                buildId: 'bd-12345_9',
                startTime: ts,
                maxDuration: Duration.ofMinutes(1),
                workspace: Path.of('/some/work/dir')
        )

        when:
        def job = factory.build(request)
        then:
        job.entryKey == 'docker.io/foo:bar'
        job.operationName == 'bd-12345-9'
        job.creationTime == ts
        job.type == JobSpec.Type.Build
        job.maxDuration == Duration.ofMinutes(1)
        job.workDir == Path.of('/some/work/dir/bd-12345_9')
    }

    def 'should create transfer job' () {
        given:
        def duration = Duration.ofMinutes(1)
        def config = new BlobCacheConfig(transferTimeout: duration)
        def factory = new JobFactory(blobConfig:config)

        when:
        def job = factory.transfer('foo-123')
        then:
        job.entryKey == 'foo-123'
        job.operationName =~ /transfer-.+/
        job.type == JobSpec.Type.Transfer
        job.maxDuration == duration
    }

    def 'should create scan job' () {
        given:
        def workdir = Path.of('/some/work/dir')
        def duration = Duration.ofMinutes(1)
        def config = new ScanConfig(timeout: duration)
        def factory = new JobFactory(scanConfig: config)
        def request = ScanRequest.of(
                scanId: 'sc-12345_1',
                buildId: 'bd-67890_2',
                configJson: '{ jsonConfig }',
                targetImage: 'docker.io/foo:bar',
                platform: ContainerPlatform.of('linux/amd64'),
                workDir: workdir
        )

        when:
        def job = factory.scan(request)
        then:
        job.entryKey == 'sc-12345_1'
        job.operationName == 'sc-12345-1'
        job.type == JobSpec.Type.Scan
        job.maxDuration == duration
        job.creationTime == request.creationTime
        job.workDir == workdir
    }

    def 'should create mirror job' () {
        given:
        def workspace = Path.of('/some/work/dir')
        def duration = Duration.ofMinutes(1)
        def config = new MirrorConfig(maxDuration: duration)
        def factory = new JobFactory(mirrorConfig: config)
        and:
        def request = MirrorRequest.create(
                'source/foo',
                'target/foo',
                'sha256:12345',
                Mock(ContainerPlatform),
                workspace,
                '{config}',
                'sc-123',
                Instant.now(),
                "GMT",
                Mock(PlatformId)
        )

        when:
        def job = factory.mirror(request)
        then:
        job.entryKey == "target/foo"
        job.operationName == request.mirrorId
        job.operationName =~ /mr-.+/
        job.type == JobSpec.Type.Mirror
        job.maxDuration == duration
        job.workDir == workspace.resolve(request.mirrorId)
        job.creationTime == request.creationTime
    }
}

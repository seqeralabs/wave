/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2025, Seqera Labs
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

import java.nio.file.Path

import io.seqera.wave.service.builder.BuildRequest
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class SingularityHelperTest extends Specification {
    def 'should modify container file with valid Bootstrap and From lines'() {
        given:
        def request = new BuildRequest(
                buildId: "123",
                workDir: "/tmp",
                workspace: Path.of("/var/workspace"),
                containerFile: """
                Bootstrap: docker
                From: ubuntu:latest
                %post
                echo "Hello World"
            """
        )

        when:
        def result = SingularityHelper.modifyContainerFileForLocalImage(request)

        then:
        result.pullContainerFile.stripIndent().replaceAll(/\s+/, ' ') == """
                Bootstrap: docker
                From: ubuntu:latest
            """.stripIndent().replaceAll(/\s+/, ' ').trim()
        and:
        result.buildContainerFile.stripIndent().replaceAll(/\s+/, ' ') == """
                Bootstrap: localimage
                From: /var/workspace/123/base_image.sif
                %post
                echo "Hello World"
           """.stripIndent().replaceAll(/\s+/, ' ')
    }

    def 'should throw exception when container file lacks Bootstrap or From lines'() {
        given:
        def request = new BuildRequest(
                buildId: "123",
                workDir: "/tmp",
                containerFile: """
                %post
                echo "Hello World"
            """
        )

        when:
        SingularityHelper.modifyContainerFileForLocalImage(request)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Container file does not contain 'Bootstrap:' or 'From:' lines for buildId: 123"
    }

    def 'should handle container file with multiple Bootstrap and From lines'() {
        given:
        def request = new BuildRequest(
                buildId: "123",
                workDir: "/tmp",
                workspace: Path.of("/var/workspace"),
                containerFile: """
                Bootstrap: docker
                From: ubuntu:latest
            """
        )

        when:
        def result = SingularityHelper.modifyContainerFileForLocalImage(request)

        then:
        result.pullContainerFile.stripIndent().replaceAll(/\s+/, ' ') == """
                Bootstrap: docker
                From: ubuntu:latest
            """.stripIndent().replaceAll(/\s+/, ' ').trim()
        and:
        result.buildContainerFile.stripIndent().replaceAll(/\s+/, ' ') == """
                Bootstrap: localimage
                From: /var/workspace/123/base_image.sif
            """.stripIndent().replaceAll(/\s+/, ' ')
    }
}

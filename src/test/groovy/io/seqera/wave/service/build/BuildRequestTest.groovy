package io.seqera.wave.service.build

import spock.lang.Specification

import java.nio.file.Path

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildRequestTest extends Specification {

    def 'should create build request' () {
        given:
        def CONTENT = 'FROM foo'
        def PATH = Path.of('somewhere')
        def repo = 'docker.io/wave'
        when:
        def req = new BuildRequest(CONTENT, PATH, repo)
        then:
        req.id == 'f567bd075aa98d429e4d7c025390e010fc8068149607ef27af21f522b633dbe0'
        req.workDir == PATH.resolve(req.id)
        req.targetImage == "docker.io/wave:${req.id}"
        req.dockerfile == CONTENT
    }
}

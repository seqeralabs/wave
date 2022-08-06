package io.seqera.wave.service.builder

import spock.lang.Specification

import java.nio.file.Path

import io.seqera.wave.tower.User

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildRequestTest extends Specification {

    def 'should create build request'() {
        given:
        def USER = new User(id:1, email: 'foo@user.com')
        def CONTENT = 'FROM foo'
        def PATH = Path.of('somewhere')
        def repo = 'docker.io/wave'
        when:
        def req = new BuildRequest(CONTENT, PATH, repo, null, USER)
        then:
        req.id == 'f567bd075aa98d429e4d7c025390e010fc8068149607ef27af21f522b633dbe0'
        req.workDir == PATH.resolve(req.id).toAbsolutePath()
        req.targetImage == "docker.io/wave:${req.id}"
        req.dockerFile == CONTENT
        req.user == USER
    }

    def 'should check equals and hash code'() {
        given:
        def USER = new User(id:1, email: 'foo@user.com')
        def PATH = Path.of('somewhere')
        def repo = 'docker.io/wave'
        and:
        def req1 = new BuildRequest('from foo', PATH, repo, null, USER)
        def req2 = new BuildRequest('from foo', PATH, repo, null, USER)
        def req3 = new BuildRequest('from bar', PATH, repo, null, USER)
        def req4 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.3', USER)
        def req5 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.3', USER)
        def req6 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.5', USER)

        expect:
        req1 == req2
        req1 != req3
        and:
        req4 == req5
        req4 != req6
        and:
        req1 != req5
        req1 != req6

        and:
        req1.hashCode() == req2.hashCode()
        req1.hashCode() != req3.hashCode()
        and:
        req4.hashCode() == req5 .hashCode()
        req4.hashCode() != req6.hashCode()
        and:
        req1.hashCode() != req5.hashCode()
        req1.hashCode() != req6.hashCode()
    }
}

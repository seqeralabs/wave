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
        def req = new BuildRequest(CONTENT, PATH, repo, null, USER, 'amd64')
        then:
        req.id == '15c52fa7417693a1173aa0d5cdb83076'
        req.workDir == PATH.resolve(req.id).toAbsolutePath()
        req.targetImage == "docker.io/wave:${req.id}"
        req.dockerFile == CONTENT
        req.user == USER
        req.job =~ /15c52fa7417693a1173aa0d5cdb83076-[a-z0-9]+/
    }

    def 'should check equals and hash code'() {
        given:
        def USER = new User(id:1, email: 'foo@user.com')
        def PATH = Path.of('somewhere')
        def repo = 'docker.io/wave'
        and:
        def req1 = new BuildRequest('from foo', PATH, repo, null, USER, 'amd64')
        def req2 = new BuildRequest('from foo', PATH, repo, null, USER, 'amd64')
        def req3 = new BuildRequest('from bar', PATH, repo, null, USER, 'amd64')
        def req4 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.3', USER, 'amd64')
        def req5 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.3', USER, 'amd64')
        def req6 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.5', USER, 'amd64')

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

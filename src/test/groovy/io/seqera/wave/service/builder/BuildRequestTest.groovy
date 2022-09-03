package io.seqera.wave.service.builder

import spock.lang.Specification

import java.nio.file.Path

import io.seqera.wave.core.ContainerPlatform
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
        def cache = 'docker.io/cache'
        when:
        def req = new BuildRequest(CONTENT, PATH, repo, null, USER, ContainerPlatform.of('amd64'),cache)
        then:
        req.id == 'acc83dc6d094823894869bf3cf3de17b'
        req.workDir == PATH.resolve(req.id).toAbsolutePath()
        req.targetImage == "docker.io/wave:${req.id}"
        req.dockerFile == CONTENT
        req.user == USER
        req.job =~ /acc83dc6d094823894869bf3cf3de17b-[a-z0-9]+/
        req.cacheRepository == cache
    }

    def 'should check equals and hash code'() {
        given:
        def USER = new User(id:1, email: 'foo@user.com')
        def PATH = Path.of('somewhere')
        def repo = 'docker.io/wave'
        def cache = 'docker.io/cache'
        and:
        def req1 = new BuildRequest('from foo', PATH, repo, null, USER, ContainerPlatform.of('amd64'), cache)
        def req2 = new BuildRequest('from foo', PATH, repo, null, USER, ContainerPlatform.of('amd64'), cache)
        def req3 = new BuildRequest('from bar', PATH, repo, null, USER, ContainerPlatform.of('amd64'), cache)
        def req4 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.3', USER, ContainerPlatform.of('amd64'), cache)
        def req5 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.3', USER, ContainerPlatform.of('amd64'), cache)
        def req6 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.5', USER, ContainerPlatform.of('amd64'), cache)

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

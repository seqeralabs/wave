package io.seqera.wave.service.builder

import spock.lang.Specification

import java.nio.file.Path
import java.time.OffsetDateTime
import java.time.ZoneId

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
        def req = new BuildRequest(CONTENT, PATH, repo, null, USER, ContainerPlatform.of('amd64'), '{auth}', cache, "")
        then:
        req.id == '86e13166182946a6d6cc80a72a8024c8'
        req.workDir == PATH.resolve(req.id).toAbsolutePath()
        req.targetImage == "docker.io/wave:${req.id}"
        req.dockerFile == CONTENT
        req.user == USER
        req.configJson == '{auth}'
        req.job =~ /86e13166182946a6d6cc80a72a8024c8-[a-z0-9]+/
        req.cacheRepository == cache
    }

    def 'should check equals and hash code'() {
        given:
        def USER = new User(id:1, email: 'foo@user.com')
        def PATH = Path.of('somewhere')
        def repo = 'docker.io/wave'
        def cache = 'docker.io/cache'
        and:
        def req1 = new BuildRequest('from foo', PATH, repo, null, USER, ContainerPlatform.of('amd64'),'{auth}', cache, "")
        def req2 = new BuildRequest('from foo', PATH, repo, null, USER, ContainerPlatform.of('amd64'),'{auth}', cache, "")
        def req3 = new BuildRequest('from bar', PATH, repo, null, USER, ContainerPlatform.of('amd64'),'{auth}', cache, "")
        def req4 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.3', USER, ContainerPlatform.of('amd64'),'{auth}', cache, "")
        def req5 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.3', USER, ContainerPlatform.of('amd64'),'{auth}', cache, "")
        def req6 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.5', USER, ContainerPlatform.of('amd64'),'{auth}', cache, "")
        def req7 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.5', USER, ContainerPlatform.of('amd64'),'{auth}', cache, "", OffsetDateTime.now(ZoneId.of("UTC+2")))

        expect:
        req1 == req2
        req1 != req3
        and:
        req4 == req5
        req4 != req6
        and:
        req1 != req5
        req1 != req6
        req1 != req7

        and:
        req1.hashCode() == req2.hashCode()
        req1.hashCode() != req3.hashCode()
        and:
        req4.hashCode() == req5 .hashCode()
        req4.hashCode() != req6.hashCode()
        and:
        req1.hashCode() != req5.hashCode()
        req1.hashCode() != req6.hashCode()

        and:
        req1.zoneId == ZoneId.systemDefault().id
        req7.zoneId == '+02:00'
    }

}

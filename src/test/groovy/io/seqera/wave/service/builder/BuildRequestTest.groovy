package io.seqera.wave.service.builder

import spock.lang.Specification

import java.nio.file.Path
import java.time.OffsetDateTime

import io.seqera.wave.api.BuildContext
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.User
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildRequestTest extends Specification {

    def 'should create docker build request'() {
        given:
        def USER = new User(id:1, email: 'foo@user.com')
        def CONTENT = 'FROM foo'
        def PATH = Path.of('somewhere')
        def BUILD_REPO = 'docker.io/wave'
        def CACHE_REPO = 'docker.io/cache'
        def SCAN_ID = '123456'
        def IP_ADDR = '10.20.30.40'
        def OFFSET = '+2'
        def CONFIG = new ContainerConfig(env: ['FOO=1'])
        def CONTEXT = Mock(BuildContext)

        when:
        def req = new BuildRequest(
                CONTENT,
                PATH,
                BUILD_REPO,
                'some conda content',
                null,
                BuildFormat.DOCKER,
                USER,
                CONFIG,
                CONTEXT,
                ContainerPlatform.of('amd64'),
                '{auth}',
                CACHE_REPO,
                SCAN_ID,
                IP_ADDR,
                OFFSET)
        then:
        req.id == 'a3edc322cba1fecfebe895785e50220b'
        req.workDir == PATH.resolve(req.id).toAbsolutePath()
        req.targetImage == "docker.io/wave:${req.id}"
        req.containerFile == CONTENT
        req.dockerFile == CONTENT
        req.user == USER
        req.configJson == '{auth}'
        req.job =~ /a3edc322cba1fecfebe895785e50220b-[a-z0-9]+/
        req.cacheRepository == CACHE_REPO
        req.format == BuildFormat.DOCKER
        req.condaFile == 'some conda content'
        req.spackFile == null
        req.platform == ContainerPlatform.of('amd64')
        req.configJson == '{auth}'
        req.scanId == SCAN_ID
        req.ip == IP_ADDR
        req.offsetId == OFFSET
        req.containerConfig == CONFIG
        req.buildContext == CONTEXT
        and:
        !req.isSpackBuild

        when:
        req = new BuildRequest(
                CONTENT,
                PATH,
                BUILD_REPO,
                null,
                'some spack content',
                BuildFormat.DOCKER,
                USER,
                CONFIG,
                CONTEXT,
                ContainerPlatform.of('amd64'),
                '{auth}',
                CACHE_REPO,
                SCAN_ID,
                IP_ADDR,
                OFFSET)
        then:
        req.id == '95b2d920006f0c41029b697ffffc2d8e'
        req.spackFile == 'some spack content'
        and:
        req.isSpackBuild
    }

    def 'should create singularity build request'() {
        given:
        def USER = new User(id:1, email: 'foo@user.com')
        def CONTENT = 'From: foo'
        def PATH = Path.of('somewhere')
        def BUILD_REPO = 'docker.io/wave'
        def CACHE_REPO = 'docker.io/cache'
        def IP_ADDR = '10.20.30.40'
        def OFFSET = '+2'
        def CONFIG = new ContainerConfig(env: ['FOO=1'])
        def CONTEXT = Mock(BuildContext)

        when:
        def req = new BuildRequest(
                CONTENT,
                PATH,
                BUILD_REPO,
                null,
                null,
                BuildFormat.SINGULARITY,
                USER,
                CONFIG,
                CONTEXT,
                ContainerPlatform.of('amd64'),
                '{auth}',
                CACHE_REPO,
                null,
                IP_ADDR,
                OFFSET)
        then:
        req.id == 'b888a49c90211559a851bb07b7a7a016'
        req.workDir == PATH.resolve(req.id).toAbsolutePath()
        req.targetImage == "oras://docker.io/wave:${req.id}"
        req.containerFile == CONTENT
        req.user == USER
        req.configJson == '{auth}'
        req.job =~ /b888a49c90211559a851bb07b7a7a016-[a-z0-9]+/
        req.cacheRepository == CACHE_REPO
        req.format == BuildFormat.SINGULARITY
        req.platform == ContainerPlatform.of('amd64')
        req.configJson == '{auth}'
        req.ip == IP_ADDR
        req.offsetId == OFFSET
        req.containerConfig == CONFIG
        req.buildContext == CONTEXT
        and:
        !req.isSpackBuild

    }

    def 'should check equals and hash code'() {
        given:
        def USER = new User(id:1, email: 'foo@user.com')
        def PATH = Path.of('somewhere')
        def repo = 'docker.io/wave'
        def cache = 'docker.io/cache'
        and:
        def req1 = new BuildRequest('from foo', PATH, repo, null, null, BuildFormat.DOCKER, USER, null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null)
        def req2 = new BuildRequest('from foo', PATH, repo, null, null, BuildFormat.DOCKER, USER, null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null)
        def req3 = new BuildRequest('from bar', PATH, repo, null, null, BuildFormat.DOCKER, USER, null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null)
        def req4 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.3', null, BuildFormat.DOCKER, USER, null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null)
        def req5 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.3', null, BuildFormat.DOCKER, USER, null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null)
        def req6 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.5', null, BuildFormat.DOCKER, USER, null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", null)
        def req7 = new BuildRequest('from bar', PATH, repo, 'salmon=1.2.5', null, BuildFormat.DOCKER, USER, null, null, ContainerPlatform.of('amd64'),'{auth}', cache, null, "", "UTC+2")

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
        req1.offsetId == OffsetDateTime.now().offset.id
        req7.offsetId == 'UTC+2'
    }

}

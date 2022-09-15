package io.seqera.wave.service.builder.cache

import spock.lang.Specification
import spock.lang.Timeout

import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ExecutionException

import io.micronaut.context.ApplicationContext
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.builder.BuildStore
import io.seqera.wave.test.RedisTestContainer
import io.seqera.wave.tower.User
import redis.clients.jedis.JedisPool
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class RedisBuildStoreTest extends Specification implements RedisTestContainer {

    ApplicationContext applicationContext

    JedisPool jedisPool

    def setup() {
        restartRedis()
        applicationContext = ApplicationContext.run([
                wave:[ build:[ timeout: '5s' ]],
                REDIS_HOST: redisHostName,
                REDIS_PORT: redisPort
        ], 'test', 'redis')
        jedisPool = new JedisPool(redisHostName, redisPort as int)
    }

    def 'should get and put key values' () {
        given:
        def USER = new User(id:1, email: 'foo@user.com')
        def PATH = Path.of('somewhere')
        def repo = 'docker.io/wave'
        def cache = 'docker.io/cache'
        def req1 = new BuildRequest('from foo', PATH, repo, null, USER, ContainerPlatform.of('amd64'), cache, "")

        def cacheStore = applicationContext.getBean(BuildStore)
        
        expect:
        cacheStore.getBuild('foo') == null
        and:
        !cacheStore.hasBuild('foo')

        when:
        cacheStore.storeBuild('foo', req1)
        then:
        cacheStore.getBuild('foo') == req1
        and:
        cacheStore.hasBuild('foo')
    }

    @Timeout(value=10)
    def 'should await for a value' () {
        given:
        def USER = new User(id:1, email: 'foo@user.com')
        def PATH = Path.of('somewhere')
        def repo = 'docker.io/wave'
        def cache = 'docker.io/cache'
        def req1 = new BuildRequest('from foo', PATH, repo, null, USER, ContainerPlatform.of('amd64'), cache, "")

        def cacheStore = applicationContext.getBean(BuildStore) as BuildStore

        when:
        // insert a value
        cacheStore.storeBuild('foo', req1)

        // update a value in a separate thread
        Thread.start {
            req1.result = new BuildResult(req1.id, -1, "test", req1.startTime, Duration.between(req1.startTime, Instant.now()))
            cacheStore.storeBuild('foo',req1)
        }

        // wait the value is updated
        sleep 1500
        def result = cacheStore.awaitBuild('foo')

        then:
        result.get() == req1
    }

    @Timeout(value=10)
    def 'should abort an await if build never finish' () {
        given:
        def USER = new User(id:1, email: 'foo@user.com')
        def PATH = Path.of('somewhere')
        def repo = 'docker.io/wave'
        def cache = 'docker.io/cache'
        def req1 = new BuildRequest('from foo', PATH, repo, null, USER, ContainerPlatform.of('amd64'), cache, "")

        def cacheStore = applicationContext.getBean(BuildStore) as BuildStore
        // insert a value
        cacheStore.storeBuild('foo', req1)

        when: "wait for an update never will arrive"
        cacheStore.awaitBuild('foo').get()
        then:
        def e = thrown(ExecutionException)
        e.cause.class == BadRequestException
    }

}

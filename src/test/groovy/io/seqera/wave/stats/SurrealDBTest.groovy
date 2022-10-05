package io.seqera.wave.stats

import spock.lang.Ignore
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.seqera.wave.configuration.RateLimiterConfig
import io.seqera.wave.exception.SlowDownException
import io.seqera.wave.ratelimit.AcquireRequest
import io.seqera.wave.ratelimit.impl.SpillwayRateLimiter
import io.seqera.wave.stats.surrealdb.SurrealClient
import io.seqera.wave.stats.surrealdb.SurrealStorage
import io.seqera.wave.test.RedisTestContainer
import io.seqera.wave.test.SurrealDBTestContainer
import redis.clients.jedis.JedisPool

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
class SurrealDBTest extends Specification implements SurrealDBTestContainer {

    ApplicationContext applicationContext

    String getSurrealDbURL() {
        "http://$surrealHostName:$surrealPort"
    }

    def setup() {
        restartDb()
        applicationContext = ApplicationContext.run([
                stats: [
                        surrealdb: [
                                user     : 'root',
                                password : 'root',
                                ns       : 'test',
                                db       : 'test',
                                url      : surrealDbURL,
                                'init-db': false
                        ]]
        ], 'test', 'surreal')
        sleep 1000L //let surrealdb starts
    }

    void "can connect"() {
        given:
        println "surrealDbURL $surrealDbURL"
        println "-"*20
        HttpClient httpClient = HttpClient.create(new URL(surrealDbURL))

        when:
        def str = httpClient.toBlocking()
                .retrieve(
                        HttpRequest.POST("/sql", "SELECT * FROM count()")
                                .headers(['ns': 'test', 'db': 'test'])
                                .basicAuth('root', 'root'), Map<String, String>)

        then:
        str.result.first() == 1
    }

    @Ignore
    void "can insert an async build"() {
        given:
        HttpClient httpClient = HttpClient.create(new URL(surrealDbURL))
        SurrealStorage storage = applicationContext.getBean(SurrealStorage)
        BuildBean build = new BuildBean(
                id: 'test',
                dockerFile: 'test',
                condaFile: 'test',
                targetImage: 'test',
                userName: 'test',
                userEmail: 'test',
                userId: 1,
                ip: '127.0.0.1',
                startTime: Instant.now(),
                duration: Duration.ofSeconds(1),
                exitStatus: 0,
        )

        when:
        storage.initializeDb()

        storage.addBuild(build)

        sleep 100 //as we are using async, let database a while to store the item
        then:
        def map = httpClient.toBlocking()
                .retrieve(
                        HttpRequest.GET("/key/build_wave")
                                .headers([
                                        'ns'          : 'test',
                                        'db'          : 'test',
                                        'User-Agent'  : 'micronaut/1.0',
                                        'Content-Type': 'application/json'])
                                .basicAuth('root', 'root'), Map<String, Object>)
        map.result.size()
        map.result.first().ip == '127.0.0.1'
    }

    @Ignore
    void "can't insert a build but ends without error"() {
        given:
        HttpClient httpClient = HttpClient.create(new URL(surrealDbURL))
        SurrealStorage storage = applicationContext.getBean(SurrealStorage)
        BuildBean build = new BuildBean(
                id: 'test',
                dockerFile: 'test',
                condaFile: 'test',
                targetImage: 'test',
                userName: 'test',
                userEmail: 'test',
                userId: 1,
                ip: '127.0.0.1',
                startTime: Instant.now(),
                duration: Duration.ofSeconds(1),
                exitStatus: 0,
        )

        when:
        surrealContainer.stop()

        storage.addBuild(build)

        sleep 100 //as we are using async, let database a while to store the item
        then:
        true
    }

}

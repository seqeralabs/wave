package io.seqera.wave.stats

import spock.lang.Specification

import java.time.Duration

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
                stats: [surrealdb: [user:'root', password:'root', ns: 'test', db: 'test', url: surrealDbURL]]
        ], 'test', 'surreal')
    }

    String getAuthorization(){
        "Basic "+"root:root".bytes.encodeBase64()
    }

    def createTables(){
        SurrealClient client = applicationContext.getBean(SurrealClient)
        def ok = client.sql( authorization, "define table build_wave SCHEMALESS")
        println ok
    }

    void "can connect"() {
        given:
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

    void "can insert a build"() {
        given:
        HttpClient httpClient = HttpClient.create(new URL(surrealDbURL))
        SurrealStorage storage = applicationContext.getBean(SurrealStorage)
        BuildBean build = new BuildBean(
                image: "image",
                userName: "userName",
                userEmail: "email",
                userId: 1,
                ip: "127.0.0.1",
                duration: Duration.ofSeconds(10),
                exitStatus: 0
        )
        createTables()
        when:
        storage.addBuild(build)
        sleep 100
        then:
        def map = httpClient.toBlocking()
                .retrieve(
                        HttpRequest.GET("/key/build_wave")
                                .headers([
                                        'ns'          : 'test',
                                        'db'          : 'test',
                                        'User-Agent'  : 'micronaut/1.0',
                                        'Content-Type': 'application/json'])
                                .basicAuth('root', 'root'), Map<String, String>)
        map.result.size()
    }

}

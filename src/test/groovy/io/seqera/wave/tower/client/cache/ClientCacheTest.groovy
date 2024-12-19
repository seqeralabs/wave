/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

package io.seqera.wave.tower.client.cache

import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration

import io.micronaut.context.ApplicationContext
import io.seqera.wave.store.cache.RedisL2TieredCache
import io.seqera.wave.test.RedisTestContainer
import io.seqera.wave.tower.User
import io.seqera.wave.tower.client.CredentialsDescription
import io.seqera.wave.tower.client.GetCredentialsKeysResponse
import io.seqera.wave.tower.client.ListCredentialsResponse
import io.seqera.wave.tower.client.UserInfoResponse
import io.seqera.wave.tower.compute.ComputeEnv
import io.seqera.wave.tower.compute.DescribeWorkflowLaunchResponse
import io.seqera.wave.tower.compute.WorkflowLaunchResponse

class ClientCacheTest extends Specification implements RedisTestContainer {

    @Shared
    ApplicationContext applicationContext

    def setup() {
        applicationContext = ApplicationContext.run([
                REDIS_HOST : redisHostName,
                REDIS_PORT : redisPort
        ], 'test', 'redis')
        sleep(500) // workaround to wait for Redis connection
    }

    def cleanup() {
        applicationContext.close()
    }

    def 'should cache user info response' () {
        given:
        def AWAIT = 150
        def store = applicationContext.getBean(RedisL2TieredCache)
        def cache1 = new ClientCacheShort(store, Duration.ofMillis(AWAIT), 100)
        def cache2= new ClientCacheShort(store, Duration.ofMillis(AWAIT), 100)
        and:
        def k = UUID.randomUUID().toString()
        def resp = new UserInfoResponse(user:new User(id:1, userName: 'paolo', email: 'p@foo.com'))

        when:
        cache1.put(k, resp)
        then:
        cache2.get(k) == resp
    }

    def 'should cache list credentials response' () {
        given:
        def AWAIT = 150
        def store = applicationContext.getBean(RedisL2TieredCache)
        def cache1 = new ClientCacheShort(store, Duration.ofMillis(AWAIT), 100)
        def cache2 = new ClientCacheShort(store, Duration.ofMillis(AWAIT), 100)
        and:
        def k = UUID.randomUUID().toString()
        def c1 = new CredentialsDescription(id: '123', provider: 'container-reg' ,registry: 'docker.io')
        def resp = new ListCredentialsResponse(credentials: [c1])

        when:
        cache1.put(k, resp)
        then:
        cache2.get(k) == resp
    }

    def 'should cache credentials keys response' () {
        given:
        def AWAIT = 150
        def store = applicationContext.getBean(RedisL2TieredCache)
        def cache1 = new ClientCacheShort(store, Duration.ofMillis(AWAIT), 100)
        def cache2 = new ClientCacheShort(store, Duration.ofMillis(AWAIT), 100)
        and:
        def k = UUID.randomUUID().toString()
        def resp = new GetCredentialsKeysResponse(keys: 'xyz')

        when:
        cache1.put(k, resp)
        then:
        cache2.get(k) == resp
    }

    def 'should cache describe workflow response' () {
        given:
        def AWAIT = 150
        def store = applicationContext.getBean(RedisL2TieredCache)
        def cache1 = new ClientCacheShort(store, Duration.ofMillis(AWAIT), 100)
        def cache2 = new ClientCacheShort(store, Duration.ofMillis(AWAIT), 100)
        and:
        def k = UUID.randomUUID().toString()
        def launch = new WorkflowLaunchResponse(computeEnv: new ComputeEnv(id: '123', platform: 'aws', credentialsId:'cred-xyz'))
        def resp = new DescribeWorkflowLaunchResponse(launch: launch)

        when:
        cache1.put(k, resp)
        then:
        cache2.get(k) == resp
    }
}

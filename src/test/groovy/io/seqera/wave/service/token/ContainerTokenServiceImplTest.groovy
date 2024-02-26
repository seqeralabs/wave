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

package io.seqera.wave.service.token

import spock.lang.Specification

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.configuration.TokenConfig
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import jakarta.inject.Inject
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
class ContainerTokenServiceImplTest extends Specification {

    @Inject
    private TokenConfig config

    @Inject
    private TokenCacheStore tokenCache

    def 'should evict container request from cache'(){
        given:
        def containerTokenService = new ContainerTokenServiceImpl( tokenCache: tokenCache, config: config )
        def TOKEN = '123abc'
        def user = new User(id: 1, userName: 'foo', email: 'foo@gmail.com')
        def data = new ContainerRequestData(new PlatformId(user,100), 'hello-world')
        and:
        tokenCache.put(TOKEN, data)

        when:
        def request = containerTokenService.evictContainerRequestFromCache(TOKEN)
        then:
        request == data
        tokenCache.get(TOKEN) == null
    }
}

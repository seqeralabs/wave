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
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import jakarta.inject.Inject
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
class ContainerRequestServiceImplTest extends Specification {

    @Inject
    private TokenConfig config

    @Inject
    private ContainerRequestStoreImpl requestStore

    def 'should evict container request from cache'(){
        given:
        def containerTokenService = new ContainerRequestServiceImpl( tokenCache: requestStore, config: config )
        def TOKEN = '123abc'
        def user = new User(id: 1, userName: 'foo', email: 'foo@gmail.com')
        def data = ContainerRequestData.of(identity: new PlatformId(user,100), containerImage: 'hello-world')
        and:
        requestStore.put(TOKEN, data)

        when:
        def request = containerTokenService.evictRequest(TOKEN)
        then:
        request == data
        requestStore.get(TOKEN) == null
    }

}

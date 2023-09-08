/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.tower.client.connector

import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.service.pairing.socket.PairingChannel
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpRequest
import io.seqera.wave.service.pairing.socket.msg.ProxyHttpResponse
import jakarta.inject.Inject
import static io.seqera.wave.service.pairing.PairingService.TOWER_SERVICE

/**
 * Implements a Tower connector using a WebSocket connection
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Slf4j
@CompileStatic
class WebSocketTowerConnector extends TowerConnector {

    @Inject
    private PairingChannel channel

    boolean isEndpointRegistered(String endpoint) {
        return channel.canHandle(TOWER_SERVICE, endpoint)
    }

    @Override
    CompletableFuture<ProxyHttpResponse> sendAsync(String endpoint, ProxyHttpRequest request) {
        return channel
                .sendRequest(TOWER_SERVICE, endpoint, request)
    }

}

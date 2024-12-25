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

package io.seqera.wave.tower.client

import java.time.Duration
import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.client.cache.ClientCache
import io.seqera.wave.tower.client.connector.TowerConnector
import io.seqera.wave.tower.compute.DescribeWorkflowLaunchResponse
import io.seqera.wave.util.RegHelper
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.apache.commons.lang3.StringUtils
/**
 * Implement a client to interact with Tower services
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class TowerClient {

    @Inject
    private TowerConnector connector

    @Inject
    private ClientCache cache

    @Value('${wave.pairing.cache.short.duration:60s}')
    private Duration cacheShortDuration

    @Value('${wave.pairing.cache.long.duration:24h}')
    private Duration cacheLongDuration


    protected <T> CompletableFuture<T> getAsync(URI uri, String endpoint, @Nullable JwtAuth authorization, Class<T> type) {
        assert uri, "Missing uri argument"
        assert endpoint, "Missing endpoint argument"
        return connector.sendAsync(endpoint, uri, authorization, type)
    }

    protected Object get0(URI uri, String endpoint, @Nullable JwtAuth auth, Class type, String cacheKey, Duration ttl) {
        log.trace "Tower client cache - key=$cacheKey; uri=$uri; ttl=$ttl; auth=${auth}"
        return cache.getOrCompute(cacheKey, (k)-> getAsync(uri, endpoint, auth, type).get(), ttl)
    }

    UserInfoResponse userInfo(String towerEndpoint, JwtAuth authorization, boolean force=false) {
        final uri = userInfoEndpoint(towerEndpoint)
        if( force )
            return getAsync(uri, towerEndpoint, authorization, UserInfoResponse).get()
        final k = RegHelper.sipHash(uri, authorization.key, null, null)
        // NOTE: it assumes the user info metadata does nor change over time
        // and therefore the *long* expiration cached is used
        get0(uri, towerEndpoint, authorization, UserInfoResponse, k, cacheLongDuration) as UserInfoResponse
    }

    ListCredentialsResponse listCredentials(String towerEndpoint, JwtAuth authorization, Long workspaceId, String workflowId) {
        final uri = listCredentialsEndpoint(towerEndpoint, workspaceId)
        final k = RegHelper.sipHash(uri, authorization.key, workspaceId, workflowId)
        // NOTE: when the 'workflowId' is provided it assumes credentials will not change during
        // the workflow execution and therefore the *long* expiration cached is used
        final ttl = workflowId ? cacheLongDuration : cacheShortDuration
        return get0(uri, towerEndpoint, authorization, ListCredentialsResponse, k, ttl) as ListCredentialsResponse
    }

    GetCredentialsKeysResponse fetchEncryptedCredentials(String towerEndpoint, JwtAuth authorization, String credentialsId, String pairingId, Long workspaceId, String workflowId) {
        final uri = fetchCredentialsEndpoint(towerEndpoint, credentialsId, pairingId, workspaceId)
        final k = RegHelper.sipHash(uri, authorization.key, workspaceId, workflowId)
        // NOTE: when the 'workflowId' is provided it assumes credentials will not change during
        // the workflow execution and therefore the *long* expiration cached is used
        final ttl = workflowId ? cacheLongDuration : cacheShortDuration
        return get0(uri, towerEndpoint, authorization, GetCredentialsKeysResponse, k, ttl) as GetCredentialsKeysResponse
    }

    protected static URI fetchCredentialsEndpoint(String towerEndpoint, String credentialsId, String pairingId, Long workspaceId) {
        if( !towerEndpoint )
            throw new IllegalArgumentException("Missing towerEndpoint argument")
        if (!credentialsId)
            throw new IllegalArgumentException("Missing credentialsId argument")
        if (!pairingId)
            throw new IllegalArgumentException("Missing encryptionKey argument")

        def uri = "${checkEndpoint(towerEndpoint)}/credentials/$credentialsId/keys?pairingId=$pairingId"
        if( workspaceId!=null )
            uri += "&workspaceId=$workspaceId"

        return URI.create(uri)
    }

    protected static URI listCredentialsEndpoint(String towerEndpoint, Long workspaceId) {
        def uri = "${checkEndpoint(towerEndpoint)}/credentials"
        if( workspaceId!=null )
            uri += "?workspaceId=$workspaceId"
        return URI.create(uri)
    }

    protected static URI userInfoEndpoint(String towerEndpoint) {
        return URI.create("${checkEndpoint(towerEndpoint)}/user-info")
    }

    static String checkEndpoint(String endpoint) {
        if( !endpoint )
            throw new IllegalArgumentException("Missing endpoint argument")
        if( !endpoint.startsWith('http://') && !endpoint.startsWith('https://') )
            throw new IllegalArgumentException("Endpoint should start with HTTP or HTTPS protocol — offending value: '$endpoint'")

        StringUtils.removeEnd(endpoint, "/")
    }

    DescribeWorkflowLaunchResponse describeWorkflowLaunch(String towerEndpoint, JwtAuth authorization, Long workspaceId, String workflowId) {
        final uri = workflowLaunchEndpoint(towerEndpoint, workspaceId, workflowId)
        final k = RegHelper.sipHash(uri, authorization.key, workspaceId, workflowId)
        // NOTE: it assumes the workflow launch definition cannot change for the specified 'workflowId'
        // and therefore the *long* expiration cached is used
        return get0(uri, towerEndpoint, authorization, DescribeWorkflowLaunchResponse.class, k, cacheLongDuration) as DescribeWorkflowLaunchResponse
    }

    protected static URI workflowLaunchEndpoint(String endpoint, Long workspaceId, String workflowId) {
        assert endpoint
        assert workflowId

        def uri = "${checkEndpoint(endpoint)}/workflow/${workflowId}/launch"
        if( workspaceId!=null )
            uri += '?workspaceId=' + workspaceId
        return URI.create(uri)
    }

    /** Only for testing - do not use */
    protected void invalidateCache() {
        cache.invalidateAll()
    }
}

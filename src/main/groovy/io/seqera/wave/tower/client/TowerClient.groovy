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

import java.util.concurrent.CompletableFuture

import com.google.common.hash.Hashing
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.client.cache.ClientCacheLong
import io.seqera.wave.tower.client.cache.ClientCacheShort
import io.seqera.wave.tower.client.connector.TowerConnector
import io.seqera.wave.tower.compute.DescribeWorkflowLaunchResponse
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

    private enum CacheMode { SHORT, LONG }

    @Inject
    private TowerConnector connector

    @Inject
    private ClientCacheShort cacheShort

    @Inject
    private ClientCacheLong cacheLong

    protected <T> CompletableFuture<T> getAsync(URI uri, String endpoint, @Nullable JwtAuth authorization, Class<T> type) {
        assert uri, "Missing uri argument"
        assert endpoint, "Missing endpoint argument"
        return connector.sendAsync(endpoint, uri, authorization, type)
    }

    protected Object get0(URI uri, String endpoint, @Nullable JwtAuth auth, Class type, String cacheKey, CacheMode mode) {
        log.trace "Tower client cache ${mode} - key=$cacheKey; uri=$uri; auth=${auth}"
        final cache = mode==CacheMode.SHORT ? cacheShort: cacheLong
        return cache.getOrCompute(cacheKey, (k)-> getAsync(uri, endpoint, auth, type).get())
    }

    UserInfoResponse userInfo(String towerEndpoint, JwtAuth authorization) {
        final uri = userInfoEndpoint(towerEndpoint)
        final k = makeKey(uri, authorization.key, null, null)
        // NOTE: it assumes the user info metadata does nor change over time
        // and therefore the *long* expiration cached is used
        get0(uri, towerEndpoint, authorization, UserInfoResponse, k, CacheMode.LONG) as UserInfoResponse
    }

    ListCredentialsResponse listCredentials(String towerEndpoint, JwtAuth authorization, Long workspaceId, String workflowId) {
        final uri = listCredentialsEndpoint(towerEndpoint, workspaceId)
        final k = makeKey(uri, authorization.key, workspaceId, workflowId)
        // NOTE: when the 'workflowId' is provided it assumes credentials will not change during
        // the workflow execution and therefore the *long* expiration cached is used
        final mode = workflowId ? CacheMode.LONG : CacheMode.SHORT
        return get0(uri, towerEndpoint, authorization, ListCredentialsResponse, k, mode) as ListCredentialsResponse
    }

    GetCredentialsKeysResponse fetchEncryptedCredentials(String towerEndpoint, JwtAuth authorization, String credentialsId, String pairingId, Long workspaceId, String workflowId) {
        final uri = fetchCredentialsEndpoint(towerEndpoint, credentialsId, pairingId, workspaceId)
        final k = makeKey(uri, authorization.key, workspaceId, workflowId)
        // NOTE: when the 'workflowId' is provided it assumes credentials will not change during
        // the workflow execution and therefore the *long* expiration cached is used
        final mode = workflowId ? CacheMode.LONG : CacheMode.SHORT
        return get0(uri, towerEndpoint, authorization, GetCredentialsKeysResponse, k, mode) as GetCredentialsKeysResponse
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

    DescribeWorkflowLaunchResponse describeWorkflowLaunch(String towerEndpoint, JwtAuth authorization, String workflowId) {
        final uri = workflowLaunchEndpoint(towerEndpoint,workflowId)
        final k = makeKey(uri, authorization.key, null, workflowId)
        // NOTE: it assumes the workflow launch definition cannot change for the specified 'workflowId'
        // and therefore the *long* expiration cached is used
        return get0(uri, towerEndpoint, authorization, DescribeWorkflowLaunchResponse.class, k, CacheMode.LONG) as DescribeWorkflowLaunchResponse
    }

    protected static URI workflowLaunchEndpoint(String towerEndpoint, String workflowId) {
        return URI.create("${checkEndpoint(towerEndpoint)}/workflow/${workflowId}/launch")
    }

    protected String makeKey(Object... keys) {
        final h = Hashing.sipHash24().newHasher()
        for( Object it :  keys ) {
            if( it!=null )
                h.putUnencodedChars(it.toString())
            h.putUnencodedChars('/')
        }
        return h.hash()
    }

    /** Only for testing - do not use */
    protected void invalidateCache() {
        cacheLong.invalidateAll()
        cacheShort.invalidateAll()
    }
}

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

    /** How long before a revoked token or a removed membership takes effect */
    @Value('${wave.access-check.cache.duration:60s}')
    private Duration accessCheckDuration

    protected <T> CompletableFuture<T> getAsync(URI uri, String endpoint, @Nullable JwtAuth authorization, Class<T> type) {
        assert uri, "Missing uri argument"
        assert endpoint, "Missing endpoint argument"
        return connector.sendAsync(endpoint, uri, authorization, type)
    }

    protected Object get0(URI uri, String endpoint, @Nullable JwtAuth auth, Class type, String cacheKey, Duration ttl) {
        log.trace "Tower client cache - key=$cacheKey; uri=$uri; ttl=$ttl; auth=${auth}"
        return cache.getOrCompute(cacheKey, (k)-> getAsync(uri, endpoint, auth, type).get(), ttl)
    }

    GetUserInfoResponse userInfo(String towerEndpoint, JwtAuth authorization, boolean force=false) {
        final uri = userInfoEndpoint(towerEndpoint)
        if( force )
            return getAsync(uri, towerEndpoint, authorization, GetUserInfoResponse).get()
        final k = RegHelper.sipHash(uri, authorization.key, null, null)
        // NOTE: it assumes the user info metadata does nor change over time
        // and therefore the *long* expiration cached is used
        get0(uri, towerEndpoint, authorization, GetUserInfoResponse, k, cacheLongDuration) as GetUserInfoResponse
    }

    /** The token is validated as presented, and cached briefly so a revoked one stops working */
    GetUserInfoResponse userInfoForAccess(String towerEndpoint, JwtAuth authorization) {
        return getAsPresented(userInfoEndpoint(towerEndpoint), towerEndpoint, authorization, GetUserInfoResponse) as GetUserInfoResponse
    }

    /** Keyed apart from the submit path's entries, which have a far longer ttl */
    private Object getAsPresented(URI uri, String endpoint, JwtAuth auth, Class type) {
        final k = RegHelper.sipHash('access', uri, auth.key)
        return cache.getOrCompute(k, (it)-> connector.sendAsyncAsPresented(endpoint, uri, auth, type).get(), accessCheckDuration)
    }

    /**
     * The organisations and workspaces the caller belongs to; Platform lists only their own.
     * The submit path: Wave authenticated this caller via the refreshing path, so the stored
     * token is used here too, but with the short access-check ttl rather than userInfo's long one.
     */
    ListWorkspacesResponse listWorkspaces(String towerEndpoint, JwtAuth authorization, Long userId) {
        final uri = listWorkspacesEndpoint(towerEndpoint, userId)
        final k = RegHelper.sipHash(uri, authorization.key, null, null)
        return get0(uri, towerEndpoint, authorization, ListWorkspacesResponse, k, accessCheckDuration) as ListWorkspacesResponse
    }

    /** The read path validates the token as presented, so it takes no newer one from the store */
    ListWorkspacesResponse listWorkspacesForAccess(String towerEndpoint, JwtAuth authorization, Long userId) {
        return getAsPresented(listWorkspacesEndpoint(towerEndpoint, userId), towerEndpoint, authorization, ListWorkspacesResponse) as ListWorkspacesResponse
    }

    protected static URI listWorkspacesEndpoint(String towerEndpoint, Long userId) {
        assert userId, "Missing userId argument"
        return URI.create("${checkEndpoint(towerEndpoint)}/user/${userId}/workspaces")
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

    DescribeWorkflowResponse describeWorkflow(String endpoint, JwtAuth authorization, Long workspaceId, String workflowId) {
        final uri = workflowDescribeEndpoint(endpoint,workspaceId,workflowId)
        final k = RegHelper.sipHash(uri, authorization.key, workspaceId, workflowId)
        // NOTE: cached for a *short* duration keyed by workflowId so the many container tokens of a
        // single run coalesce onto one Platform lookup per window instead of one lookup per token.
        // Completion is thus observed up to cacheShortDuration late — harmless, since the token
        // access-ttl (minutes) is what bounds post-completion access, not this cache.
        return get0(uri, endpoint, authorization, DescribeWorkflowResponse, k, cacheShortDuration) as DescribeWorkflowResponse
    }

    protected static URI workflowDescribeEndpoint(String endpoint, Long workspaceId, String workflowId) {
        // only the workflow status is needed: 'attributes=minimal' asks Platform to omit the large fields
        // (params, config, error report, progress). Platform versions not supporting it ignore the value
        def uri = "${checkEndpoint(endpoint)}/workflow/${workflowId}?attributes=minimal"
        if( workspaceId!=null )
            uri += "&workspaceId=$workspaceId"
        return URI.create(uri)
    }

    /** Only for testing - do not use */
    protected void invalidateCache() {
        cache.invalidateAll()
    }
}

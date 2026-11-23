/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.tower

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.seqera.serde.moshi.MoshiSerializable
import io.seqera.wave.api.Visibility


/**
 * Model the Platform tenant a Wave record belongs to.
 *
 * Unlike {@link PlatformId} this holds no access token and is safe to persist: it is the
 * identity a record keeps after the request that created it is gone.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@ToString(includeNames = true, includePackage = false)
@EqualsAndHashCode
@CompileStatic
class RecordOwner implements MoshiSerializable {

    /**
     * The Platform this record belongs to, stored exactly as the request carried it.
     *
     * This is the address the access check dials, and the pairing lookup resolves it by the
     * spelling Platform itself registered - the submit path already refused anything else. A
     * normalised value could miss both, and the owner is persisted, so that would need a
     * backfill. The tenant key uses this value as stored.
     */
    String towerEndpoint

    Long userId

    /** Null when the request named no workspace, which means the user's personal workspace */
    Long workspaceId

    /**
     * The tenant this record belongs to: the workspace when there is one, otherwise the user.
     *
     * The endpoint is used exactly as paired, never canonicalised. Pairing, user-info and the
     * workspace listing all resolve the raw spelling, so folding two spellings into one tenant
     * would let a Platform paired under another spelling of the same host share this tenant's
     * ids while still answering its own membership checks.
     */
    String tenant() {
        // not `workspaceId ?:` - 0L is falsy in Groovy
        return workspaceId != null ? "$towerEndpoint:$workspaceId" : "$towerEndpoint:user:$userId"
    }

    /**
     * @return The tenant to hash into a private build id, or {@code null} when the build is
     *      public or anonymous - in which case the id is unchanged from before this existed.
     */
    static String tenantOf(PlatformId identity, String visibility) {
        return tenantOf(of(identity), visibility)
    }

    /** The same tenant seen from a stored record, so a cached one can be matched to a new request */
    static String tenantOf(RecordOwner owner, String visibility) {
        return Visibility.isPrivate(visibility) ? owner?.tenant() : null
    }

    static RecordOwner of(PlatformId identity) {
        // an endpoint alone doesn't make a record owned: ContainerController fills in the
        // default Platform endpoint before it handles anonymous requests
        if( identity?.userId == null )
            return null
        new RecordOwner(
                towerEndpoint: identity.towerEndpoint,
                userId: identity.userId,
                workspaceId: identity.workspaceId )
    }

}

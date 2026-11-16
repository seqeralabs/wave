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

package io.seqera.wave.tower

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.seqera.serde.moshi.MoshiSerializable

import static io.seqera.wave.util.ContainerHelper.normalisePlatformEndpoint

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

    /** User and workspace ids are unique only within one Platform, so neither identifies a tenant without this */
    String towerEndpoint

    Long userId

    /** Null when the request named no workspace, which means the user's personal workspace */
    Long workspaceId

    static RecordOwner of(PlatformId identity) {
        // an endpoint alone doesn't make a record owned: ContainerController fills in the
        // default Platform endpoint before it handles anonymous requests
        if( identity?.userId == null )
            return null
        new RecordOwner(
                towerEndpoint: normalisePlatformEndpoint(identity.towerEndpoint),
                userId: identity.userId,
                workspaceId: identity.workspaceId )
    }

}

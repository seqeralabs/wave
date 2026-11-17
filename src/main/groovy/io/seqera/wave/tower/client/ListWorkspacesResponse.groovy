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

package io.seqera.wave.tower.client

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.seqera.serde.moshi.MoshiSerializable

/**
 * Platform's reply to {@code GET /user/{userId}/workspaces}: one row per role the caller holds.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@EqualsAndHashCode
@ToString(includePackage = false, includeNames = true)
@CompileStatic
class ListWorkspacesResponse implements MoshiSerializable {

    List<OrgAndWorkspace> orgsAndWorkspaces

    @EqualsAndHashCode
    @ToString(includePackage = false, includeNames = true)
    @CompileStatic
    static class OrgAndWorkspace implements MoshiSerializable {
        Long orgId
        String orgName
        Long workspaceId
        String workspaceName
        Set<String> roles
    }

    /**
     * @return {@code true} when the caller holds a role in that workspace. A null argument never
     *      matches, because the org rows carry a null workspace id.
     */
    boolean hasWorkspace(Long workspaceId) {
        return workspaceId != null && orgsAndWorkspaces?.any { it.workspaceId == workspaceId }
    }

}

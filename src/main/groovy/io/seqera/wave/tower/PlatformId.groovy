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

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.seqera.wave.api.ContainerInspectRequest
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.util.RegHelper
import io.seqera.wave.util.StringUtils

/**
 * Model Seqera Platform aka Tower identity
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class PlatformId {

    static final PlatformId NULL = new PlatformId()

    final User user
    final Long workspaceId
    final String accessToken
    final String towerEndpoint
    final String workflowId

    boolean asBoolean() {
        user!=null || workspaceId!=null || accessToken || towerEndpoint || workflowId
    }

    Long getUserId() {
        return user?.id
    }

    String getUserEmail() {
        return user?.email
    }

    static PlatformId of(User user, SubmitContainerTokenRequest request) {
        new PlatformId(
                user,
                request.towerWorkspaceId,
                request.towerAccessToken,
                request.towerEndpoint,
                request.workflowId)
    }

    static PlatformId of(User user, ContainerInspectRequest request) {
        new PlatformId(
                user,
                request.towerWorkspaceId,
                request.towerAccessToken,
                request.towerEndpoint)
    }

    @Override
    String toString() {
        return "PlatformId(" +
                "user=" + user +
                ", workspaceId=" + workspaceId +
                ", accessToken=" + StringUtils.trunc(accessToken,25) +
                ", towerEndpoint=" + towerEndpoint +
                ", workflowId=" + workflowId +
                ')';
    }

    String stableHash() {
        RegHelper.sipHash(
                getUserId(),
                getUserEmail(),
                workspaceId,
                accessToken,
                towerEndpoint,
                workflowId )
    }
}

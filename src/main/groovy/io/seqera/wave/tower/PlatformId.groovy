/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

    boolean asBoolean() {
        user!=null || workspaceId!=null || accessToken || towerEndpoint
    }

    Long getUserId() {
        return user?.id
    }

    static PlatformId of(User user, SubmitContainerTokenRequest request) {
        new PlatformId(
                user,
                request.towerWorkspaceId,
                request.towerAccessToken,
                request.towerEndpoint)
    }

    static PlatformId of(User user, ContainerInspectRequest request) {
        new PlatformId(
                user,
                request.towerWorkspaceId,
                request.towerAccessToken,
                request.towerEndpoint)
    }
}

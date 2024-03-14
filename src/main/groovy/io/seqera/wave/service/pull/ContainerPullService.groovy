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

package io.seqera.wave.service.pull

import io.seqera.wave.core.RoutePath
/**
 * Defines the interface for wave container pull service
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
interface ContainerPullService {

    /**
     * create and save wave pull into persistence
     *
     * @param RoutePath , a container registry route path
     * @param ipAddress , ipAddress of the user
     */
    void createWaveContainerPullRecord(RoutePath route, String ipAddress)
}

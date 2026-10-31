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

package io.seqera.wave.service.soci.strategy

import groovy.transform.CompileStatic
import io.seqera.wave.service.soci.SociIndexRequest
/**
 * Launch the {@code soci-indexer} job building the zTOC of one layer
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
abstract class SociIndexStrategy {

    /** Where the docker auth file is mounted in the job container, see {@code DOCKER_CONFIG} */
    static final String DOCKER_CONFIG_DIR = '/tmp/docker'

    abstract void launchJob(String jobName, SociIndexRequest request)

    protected List<String> indexCommand(SociIndexRequest request) {
        return List.of(
                'soci-indexer',
                'index',
                '-source', request.source,
                '-digest', request.layerDigest,
                '-media-type', request.layerMediaType,
                '-span', String.valueOf(request.spanSize),
                '-min-size', String.valueOf(request.minLayerSize),
                '-repo', request.repository )
    }
}

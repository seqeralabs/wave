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

package io.seqera.wave.util

import groovy.transform.CompileStatic
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodList
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.exception.BadRequestException

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class K8sHelper {

    /**
     * Given the requested container platform and collection of node selector labels find the best
     * matching label
     *
     * @param platform
     *      The requested container platform e.g. {@code linux/amd64}
     * @param nodeSelectors
     *      A map that associate the platform architecture with a corresponding node selector label
     * @return
     *      A {@link Map} object representing a kubernetes label to be used as node selector for the specified
     *      platform or a empty map when there's no matching
     */
    static Map<String,String> getSelectorLabel(ContainerPlatform platform, Map<String,String> nodeSelectors) {
        if( !nodeSelectors )
            return Collections.emptyMap()

        final key = platform.toString()
        if( nodeSelectors.containsKey(key) ) {
            return toLabelMap(nodeSelectors[key])
        }

        final allKeys = nodeSelectors.keySet().sort( it -> it.size() ).reverse()
        for( String it : allKeys ) {
            if( ContainerPlatform.of(it) == platform ) {
                return toLabelMap(nodeSelectors[it])
            }
        }

        throw new BadRequestException("Unsupported container platform '${platform}'")
    }

    /**
     * Given a label formatted as key=value, return it as a map
     *
     * @param label A label composed by a key and a value, separated by a `=` character.
     * @return The same label as Java {@link Map} object
     */
    static private Map<String,String> toLabelMap(String label) {
        final parts = label.tokenize('=')
        if( parts.size() != 2 )
            throw new IllegalArgumentException("Label should be specified as 'key=value' -- offending value: '$label'")
        return Map.of(parts[0], parts[1])
    }

    static V1Pod findLatestPod(V1PodList allPods) {
        // Find the latest created pod among the pods associated with the job
        def latest = allPods.getItems().get(0)
        for (def pod : allPods.items) {
            if (pod.metadata?.creationTimestamp?.isAfter(latest.metadata.creationTimestamp)) {
                latest = pod
            }
        }
        return latest
    }
}

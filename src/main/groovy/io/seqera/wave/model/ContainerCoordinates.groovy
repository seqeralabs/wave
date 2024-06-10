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

package io.seqera.wave.model

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.seqera.wave.core.ContainerPath
import io.seqera.wave.util.StringUtils
import static io.seqera.wave.WaveDefault.DOCKER_IO
/**
 * Model a container image coordinates
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class ContainerCoordinates implements ContainerPath {

    final String scheme
    final String registry
    final String image
    final String reference

    String getRepository() { image ? "$registry/$image" : null }

    String getTargetContainer() {
        final result = registry + '/' + getImageAndTag()
        return scheme ? "$scheme://$result" : result
    }

    String getImageAndTag() {
        if( !reference ) return image
        final sep = reference.startsWith('sha256:') ? '@' : ':'
        return image + sep + reference
    }

    static ContainerCoordinates parse(String path) {
        if( !path )
            throw new IllegalArgumentException("Container image name is not provided")

        final scheme = StringUtils.getUrlProtocol(path)
        if( scheme ) {
            if( scheme!='oras') throw new IllegalArgumentException("Invalid container scheme: '$scheme' - offending image: '$path'")
            path = path.substring(7)
        }

        final coordinates = path.tokenize('/')

        String ref
        def last = coordinates.size()-1
        int pos
        if( (pos=coordinates[last].indexOf('@'))!=-1 || (pos=coordinates[last].indexOf(':'))!=-1 ) {
            def name = coordinates[last]
            ref = name.substring(pos+1)
            coordinates[last] = name.substring(0,pos)
        }
        else {
           ref = 'latest'
        }

        // check if it's registry host name
        String reg=null
        if( coordinates[0].contains('.') || coordinates[0].contains(':') ) {
            reg = coordinates[0]; coordinates.remove(0)
        }
        // default to docker registry
        reg ?= DOCKER_IO
        if( !isValidRegistry(reg) || !ref )
            throw new IllegalArgumentException("Invalid container image name: $path")

        // add default library prefix to docker images
        if( coordinates.size()==1 && reg==DOCKER_IO ) {
            coordinates.add(0,'library')
        }

        final image = coordinates.join('/')
        return new ContainerCoordinates(scheme, reg, image, ref)
    }

    static boolean isValidRegistry(String name) {
        if( !name )
            return false
        final p = name.indexOf(':')
        return p==-1 || name.substring(p+1).isInteger()
    }
}

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

package io.seqera.wave.storage;

import java.util.Base64;

import io.seqera.wave.api.ContainerLayer;
import io.seqera.wave.model.ContentType;

import static io.seqera.wave.util.StringUtils.trunc;

/**
 * Implement a factory class for {@link DigestStore} objects
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class DigestStoreFactory {

    static DigestStore of(ContainerLayer layer) {
        if( layer==null )
            throw new IllegalArgumentException("Missing 'layer' argument");
        if( layer.location==null )
            throw new IllegalArgumentException(("Missing 'layer.location' argument"));

        final String type = ContentType.DOCKER_IMAGE_TAR_GZIP;
        final String location = layer.location;
        final String digest = layer.gzipDigest;
        final Integer size = layer.gzipSize;

        if( location.startsWith("docker://") ) {
            return new DockerDigestStore(location, type, digest, size);
        }
        if( location.startsWith("http://") || layer.location.startsWith("https://")) {
            return new HttpDigestStore(location, type, digest, size);
        }
        if( location.startsWith("data:")) {
            final byte[] data = Base64.getDecoder().decode(location.substring(5));
            return ZippedDigestStore.fromUncompressed(data, type, digest, size);
        }
        if( location.startsWith("gzip:") ) {
            final byte[] data = Base64.getDecoder().decode(location.substring(5));
            return ZippedDigestStore.fromCompressed(data, type, digest, size);
        }
        throw new IllegalArgumentException("Unsupported location type: " + trunc(location, 100));
    }

}

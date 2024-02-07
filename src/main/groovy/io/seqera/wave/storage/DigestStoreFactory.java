package io.seqera.wave.storage;

import java.util.Base64;

import io.seqera.wave.api.ContainerLayer;
import io.seqera.wave.model.ContentType;

import static io.seqera.wave.util.StringUtils.trunc;

/**
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
